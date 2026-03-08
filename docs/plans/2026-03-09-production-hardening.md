# Production Hardening Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Fix all confirmed security, crash, data-safety, thread-safety, and performance issues to make ToolNeuron production-ready.

**Architecture:** Fixes are grouped into 6 phases by risk/impact. Each phase is independently committable. No new features — only hardening existing code.

**Tech Stack:** Kotlin, Jetpack Compose, Android Manifest XML, ProGuard, JNI/C++, ConcurrentHashMap, AtomicBoolean, coroutines

**Memory-Vault Note:** memory-vault is legacy read-only code (replaced by UMS). All write-related vault issues (WAL recovery, checkpoint atomicity, migration) are OUT OF SCOPE. Only read-path stability matters.

---

## Phase 1: Security (CRITICAL — do first)

### Task 1: Fix Network Security Config

**Files:**
- Modify: `app/src/main/res/xml/network_security_config.xml`

**Step 1: Fix the config**

Replace the entire file with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <base-config cleartextTrafficPermitted="false">
        <trust-anchors>
            <certificates src="system" />
        </trust-anchors>
    </base-config>
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="true">localhost</domain>
        <domain includeSubdomains="true">127.0.0.1</domain>
    </domain-config>
    <debug-overrides>
        <trust-anchors>
            <certificates src="user" />
        </trust-anchors>
    </debug-overrides>
</network-security-config>
```

Changes:
- `base-config cleartextTrafficPermitted` → `false` (was `true`)
- Removed `<certificates src="user" />` from base-config (MITM risk)
- Added `<debug-overrides>` for user certs (debug builds only)
- Kept localhost cleartext for local inference server

**Step 2: Commit**

```bash
git add app/src/main/res/xml/network_security_config.xml
git commit -m "security: disable cleartext traffic and user certs in release builds"
```

---

### Task 2: Harden AndroidManifest — Services and Activities

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`

**Step 1: Add signature permission and fix exported components**

1. Add custom signature permission before `<application>`:
```xml
<permission
    android:name="com.dark.tool_neuron.permission.BIND_LLM_SERVICE"
    android:protectionLevel="signature" />
```

2. Change `LLMService` permission from `android.permission.FOREGROUND_SERVICE_DATA_SYNC` to `com.dark.tool_neuron.permission.BIND_LLM_SERVICE`

3. Set `ModelPickerActivity` to `android:exported="false"` (only started internally from HomeTopBar)

4. Set `ModelLoadingActivity` to `android:exported="false"` (only started internally)

**Step 2: Commit**

```bash
git add app/src/main/AndroidManifest.xml
git commit -m "security: add signature permission for LLMService, unexport activities"
```

---

### Task 3: Fix Zip Slip Vulnerability in ModelDownloadService

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/service/ModelDownloadService.kt`

**Step 1: Add canonical path validation in unzipFile()**

After the line that constructs `val file = File(destDir, fileName)` (around line 462), add:

```kotlin
require(file.canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
    "Zip entry path traversal detected: ${entry.name}"
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/service/ModelDownloadService.kt
git commit -m "security: add Zip Slip path traversal check in unzipFile"
```

---

### Task 4: Configure Backup Rules

**Files:**
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`
- Modify: `app/src/main/res/xml/backup_rules.xml`

**Step 1: Configure data_extraction_rules.xml (API 31+)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="." />
        <exclude domain="file" path="vault" />
        <exclude domain="file" path="models" />
        <exclude domain="file" path="ums" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="." />
        <exclude domain="file" path="vault" />
    </device-transfer>
</data-extraction-rules>
```

**Step 2: Configure backup_rules.xml (API < 31)**

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>
    <exclude domain="database" path="." />
    <exclude domain="file" path="vault" />
    <exclude domain="file" path="models" />
    <exclude domain="file" path="ums" />
</full-backup-content>
```

**Step 3: Commit**

```bash
git add app/src/main/res/xml/data_extraction_rules.xml app/src/main/res/xml/backup_rules.xml
git commit -m "security: configure backup rules to exclude databases, vault, and models"
```

---

## Phase 2: Crash Prevention (CRITICAL)

### Task 5: Fix DiffusionEngine lateinit Crash

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/engine/DiffusionEngine.kt`

**Step 1: Replace lateinit with nullable**

Change line 34 from:
```kotlin
private lateinit var sdManager: StableDiffusionManager
```
to:
```kotlin
private var sdManager: StableDiffusionManager? = null
```

Update all property getters (lines 39-47) to use safe access with fallback. For StateFlow properties, provide a default:
```kotlin
val backendState: StateFlow<DiffusionBackendState>
    get() = sdManager?.diffusionBackendState ?: MutableStateFlow(DiffusionBackendState.Idle)
val generationState: StateFlow<DiffusionGenerationState>
    get() = sdManager?.diffusionGenerationState ?: MutableStateFlow(DiffusionGenerationState.Idle)
val isGenerating: StateFlow<Boolean>
    get() = sdManager?.isGenerating ?: MutableStateFlow(false)
```

Apply same pattern to upscaleState, segmenterState, lamaState, depthState, styleState.

Update `init()` to assign: `sdManager = StableDiffusionManager(context)`

Update all methods that use `sdManager` to use `sdManager?.` or `val mgr = sdManager ?: return`.

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/engine/DiffusionEngine.kt
git commit -m "fix: replace lateinit sdManager with nullable to prevent crash before init"
```

---

### Task 6: Fix LLMService.onDestroy() Cleanup Race

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/service/LLMService.kt`

**Step 1: Fix onDestroy (around line 450)**

Replace:
```kotlin
override fun onDestroy() {
    instance = null
    scope.launch {
        ggufEngine.unload()
        diffusionEngine.cleanup()
    }
    scope.cancel()
    super.onDestroy()
    Log.i(TAG, "LLMService destroyed")
}
```

With:
```kotlin
override fun onDestroy() {
    instance = null
    runBlocking(Dispatchers.IO) {
        ggufEngine.unload()
        diffusionEngine.cleanup()
    }
    scope.cancel()
    super.onDestroy()
    Log.i(TAG, "LLMService destroyed")
}
```

Add import for `kotlinx.coroutines.runBlocking` if not present.

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/service/LLMService.kt
git commit -m "fix: use runBlocking in onDestroy to ensure native cleanup completes"
```

---

### Task 7: Fix VaultHelper.initialize() — Stop Deleting All Data on Error

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/vault/VaultHelper.kt`

**Step 1: Fix catch block (around line 195)**

Replace the catch block that calls `vaultDir.deleteRecursively()` with:
```kotlin
catch (e: Exception) {
    e.printStackTrace()
    VaultLogger.log(LogLevel.ERROR, "INIT", "Vault initialization failed: ${e.message}")
    initialized = false
    _isReady.value = false
    // Do NOT delete vault data — it may be recoverable
    // Surface the error so the app can show a recovery UI
}
```

Remove the recovery logic that creates a fresh vault and sets `initialized = true`. A failed init should stay failed — the app should handle the `isReady = false` state gracefully.

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/vault/VaultHelper.kt
git commit -m "fix: stop silently deleting all vault data on init failure"
```

---

### Task 8: Fix Hardcoded 512x512 in LlmModelWorker

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/worker/LlmModelWorker.kt`

**Step 1: Fix AIDL call (around line 766)**

Replace the two hardcoded `512` values with the `width` and `height` parameters:

```kotlin
svc.generateDiffusionImage(
    prompt,
    negativePrompt,
    steps,
    cfgScale,
    seed,
    width,    // was: 512
    height,   // was: 512
    scheduler,
    ...
)
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/worker/LlmModelWorker.kt
git commit -m "fix: pass actual width/height to diffusion AIDL instead of hardcoded 512"
```

---

### Task 9: Fix ChatViewModel sendImageRequest Double-Tap

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Step 1: Set isGenerating synchronously (around line 1540)**

At the top of `sendImageRequest`, before the coroutine launch, add:
```kotlin
fun sendImageRequest(...) {
    if (_chatUiState.value.isGenerating) return
    _chatUiState.update { it.copy(isGenerating = true) }  // set BEFORE launch

    viewModelScope.launch {
        try {
            // ... existing body ...
        } finally {
            resetStreamingState()
        }
    }
}
```

Ensure `resetStreamingState()` is in a `finally` block inside the coroutine so it always runs.

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt
git commit -m "fix: set isGenerating synchronously in sendImageRequest to prevent double-tap"
```

---

## Phase 3: Thread Safety (HIGH)

### Task 10: Fix RagRepository — ConcurrentHashMap for loadedGraphs/passwordCache

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/repo/RagRepository.kt`

**Step 1: Replace plain maps (lines 30, 33)**

```kotlin
private val loadedGraphs = java.util.concurrent.ConcurrentHashMap<String, NeuronGraph>()
private val passwordCache = java.util.concurrent.ConcurrentHashMap<String, String>()
```

In `queryAllLoadedGraphs` and `queryAllLoadedGraphsWithPipeline`, snapshot before iterating:
```kotlin
val snapshot = loadedGraphs.toMap()
for ((ragId, graph) in snapshot) { ... }
```

**Step 2: Fix NeuronPacketManager leak in loadGraph (around line 370)**

Wrap packet operations in try-finally:
```kotlin
val packetManager = NeuronPacketManager()
try {
    packetManager.open(file)
    val authResult = packetManager.authenticate(effectivePassword)
    if (authResult.isFailure) {
        return@withContext Result.failure(...)
    }
    val payloadResult = packetManager.decryptPayload(authResult.getOrThrow())
    if (payloadResult.isFailure) {
        return@withContext Result.failure(...)
    }
    // ... deserialize ...
} finally {
    packetManager.close()
}
```

**Step 3: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/repo/RagRepository.kt
git commit -m "fix: use ConcurrentHashMap for loadedGraphs/passwordCache, fix packet leak"
```

---

### Task 11: Fix ModelDownloadService — ConcurrentHashMap for downloadJobs

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/service/ModelDownloadService.kt`

**Step 1: Replace line 42**

```kotlin
private val downloadJobs = java.util.concurrent.ConcurrentHashMap<String, Job>()
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/service/ModelDownloadService.kt
git commit -m "fix: use ConcurrentHashMap for downloadJobs to prevent race conditions"
```

---

### Task 12: Fix ChatViewModel userMessageAdded — AtomicBoolean

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Step 1: Replace @Volatile var with AtomicBoolean (line 136)**

```kotlin
private val userMessageAdded = java.util.concurrent.atomic.AtomicBoolean(false)
```

Update all usages:
- `userMessageAdded = false` → `userMessageAdded.set(false)`
- `userMessageAdded = true` → `userMessageAdded.set(true)`
- `if (!userMessageAdded)` → `if (!userMessageAdded.get())`
- For check-then-act: `if (userMessageAdded.compareAndSet(false, true)) { _messages.add(...) }`

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt
git commit -m "fix: use AtomicBoolean for userMessageAdded to prevent race condition"
```

---

### Task 13: Fix MemoryViewModel isVaultInitialized

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/MemoryViewModel.kt`

**Step 1: Add @Volatile (line 40)**

```kotlin
@Volatile private var isVaultInitialized = false
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/viewmodel/MemoryViewModel.kt
git commit -m "fix: add @Volatile to isVaultInitialized for cross-thread visibility"
```

---

### Task 14: Fix PluginManager Cache Atomicity

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/plugins/PluginManager.kt`

**Step 1: Fix getEnabledToolDefinitions (around line 324)**

Replace check-then-compute with synchronized:
```kotlin
fun getEnabledToolDefinitions(): List<ToolDefinitionBuilder> {
    _cachedEnabledToolDefs?.let { return it }
    synchronized(this) {
        _cachedEnabledToolDefs?.let { return it }
        val defs = _plugins.values
            .filter { it.isEnabled }
            .flatMap { it.getToolDefinitions() }
        _cachedEnabledToolDefs = defs
        return defs
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/plugins/PluginManager.kt
git commit -m "fix: synchronized cache access in getEnabledToolDefinitions"
```

---

## Phase 4: DI / Architecture Fixes (HIGH)

### Task 15: Fix HuggingFaceExplorerRepository — jakarta → javax

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/repo/HuggingFaceExplorerRepository.kt`

**Step 1: Fix imports (lines 4-5)**

```kotlin
// Change:
import jakarta.inject.Inject
import jakarta.inject.Singleton
// To:
import javax.inject.Inject
import javax.inject.Singleton
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/repo/HuggingFaceExplorerRepository.kt
git commit -m "fix: use javax.inject instead of jakarta.inject for Hilt compatibility"
```

---

### Task 16: Remove Duplicate ChatManager from AppContainer

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/di/AppContainer.kt`

**Step 1: Remove unused ChatManager (line 24)**

Delete the line:
```kotlin
private val chatManager = ChatManager()
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/di/AppContainer.kt
git commit -m "fix: remove duplicate ChatManager from AppContainer (Hilt provides it)"
```

---

## Phase 5: UI Performance & Safety (HIGH/MEDIUM)

### Task 17: Fix SystemTab — Remove Composition-Time IPC

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/home/SystemTab.kt`

**Step 1: Memoize system metrics (around lines 108-110, 192-214)**

Replace direct calls with `produceState`:
```kotlin
val memoryUsage by produceState("--", Unit) {
    withContext(Dispatchers.IO) {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val usedMemory = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
        val totalMemory = memoryInfo.totalMem / (1024 * 1024)
        value = "${usedMemory}MB / ${totalMemory}MB"
    }
}

val activeThreads by produceState("--", Unit) {
    value = "${Thread.activeCount()}"
}
```

Remove `getMemoryUsage()` and `getActiveThreads()` composable functions. Use the `produceState` values at lines 108/110.

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/home/SystemTab.kt
git commit -m "fix: move system metrics out of composition to prevent per-frame IPC"
```

---

### Task 18: Fix InstalledModelsTab — File.walkTopDown Off Main Thread

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/model_store/InstalledModelsTab.kt`

**Step 1: Move file size calculation to produceState (around lines 179-196)**

Replace inline file size calculation with:
```kotlin
val sizeText by produceState("Calculating...", model.modelPath) {
    value = withContext(Dispatchers.IO) {
        val modelFile = File(model.modelPath)
        if (modelFile.exists()) {
            val sizeBytes = if (modelFile.isDirectory) {
                modelFile.walkTopDown().sumOf { it.length() }
            } else {
                modelFile.length()
            }
            formatBytes(sizeBytes)
        } else "Not found"
    }
}
```

Apply same fix to `ModelDetailsDialog()` (around lines 273-279).

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/model_store/InstalledModelsTab.kt
git commit -m "fix: move File.walkTopDown to IO dispatcher to prevent ANR"
```

---

### Task 19: Fix HomeDrawerScreen — isDeleting State Reset

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/home/HomeDrawerScreen.kt`

**Step 1: Add callback for delete completion (around line 217)**

Change `ChatListItem` signature to include a result callback:
```kotlin
private fun ChatListItem(
    chat: ChatInfo,
    onClick: () -> Unit,
    onDelete: (onComplete: () -> Unit) -> Unit  // changed
)
```

Update the delete button:
```kotlin
IconButton(
    onClick = {
        isDeleting = true
        onDelete { isDeleting = false }
    }
)
```

Update the caller to invoke `onComplete()` after delete finishes (both success and failure paths).

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/home/HomeDrawerScreen.kt
git commit -m "fix: reset isDeleting state after delete completes or fails"
```

---

### Task 20: Fix ImageGeneration Bitmap Leak

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/home/ImageGeneration.kt`

**Step 1: Add DisposableEffect to recycle bitmaps (after the remember block around line 108)**

```kotlin
val chunks = remember(bitmap) {
    // ... existing chunk creation ...
}

DisposableEffect(chunks) {
    onDispose {
        chunks.forEach { it.recycle() }
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/home/ImageGeneration.kt
git commit -m "fix: recycle feathered chunk bitmaps on dispose to prevent OOM"
```

---

### Task 21: Fix MarkdownText Cache TOCTOU

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/components/MarkdownText.kt`

**Step 1: Use computeIfAbsent (around line 188)**

Replace the manual cache logic with:
```kotlin
private fun parseMarkdownCached(text: String): List<MarkdownElement> {
    return parseCache.computeIfAbsent(text) {
        if (parseCache.size >= 16) {
            val keysToRemove = parseCache.keys.take(parseCache.size / 2)
            keysToRemove.forEach { parseCache.remove(it) }
        }
        parseMarkdown(text)
    }
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/components/MarkdownText.kt
git commit -m "fix: use computeIfAbsent in parseCache to prevent TOCTOU race"
```

---

## Phase 6: Build Hardening (MEDIUM)

### Task 22: Enable shrinkResources + Fix ProGuard

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Add shrinkResources (around line 31)**

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro"
    )
}
```

**Step 2: Commit**

```bash
git add app/build.gradle.kts
git commit -m "build: enable shrinkResources for release builds"
```

---

## Issue Tracking Summary

| Task | Phase | Severity | Issue |
|------|-------|----------|-------|
| 1 | Security | CRITICAL | Cleartext traffic + user certs in release |
| 2 | Security | HIGH | LLMService/Activities exported without protection |
| 3 | Security | HIGH | Zip Slip in unzipFile |
| 4 | Security | HIGH | Backup rules unconfigured |
| 5 | Crash | CRITICAL | DiffusionEngine lateinit crash |
| 6 | Crash | CRITICAL | LLMService onDestroy cleanup race |
| 7 | Crash | CRITICAL | VaultHelper deletes all data on init error |
| 8 | Crash | CRITICAL | Hardcoded 512x512 image dimensions |
| 9 | Crash | CRITICAL | sendImageRequest double-tap race |
| 10 | Thread | HIGH | RagRepository plain HashMap + packet leak |
| 11 | Thread | HIGH | ModelDownloadService plain HashMap |
| 12 | Thread | HIGH | userMessageAdded @Volatile insufficient |
| 13 | Thread | HIGH | isVaultInitialized missing @Volatile |
| 14 | Thread | HIGH | PluginManager cache non-atomic |
| 15 | DI | HIGH | jakarta.inject breaks Hilt singleton |
| 16 | DI | MEDIUM | Duplicate ChatManager instance |
| 17 | UI | CRITICAL | SystemTab IPC in composition |
| 18 | UI | CRITICAL | File.walkTopDown on main thread |
| 19 | UI | HIGH | isDeleting state never resets |
| 20 | UI | HIGH | Bitmap OOM leak |
| 21 | UI | MEDIUM | MarkdownText cache TOCTOU |
| 22 | Build | MEDIUM | shrinkResources not enabled |

---

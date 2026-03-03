# UMS Storage Migration Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Replace Room DB + VaultHelper with UMS (Universal Message Store), add optional encryption, copy onboarding UI from ToolNeuron2, and fix critical performance issues.

**Architecture:** Three source modules (system_encryptor, file_ops, ums) ported from ToolNeuron2. UMS replaces 7 Room entities and the encrypted VaultHelper. DataStore and NeuronPacket stay. Security mode (regular/protected) chosen on first launch.

**Tech Stack:** Kotlin, Jetpack Compose, C++17 (JNI), BoringSSL, LZ4, Hilt DI

---

## Phase 1: Port Native Modules

### Task 1.1: Copy system_encryptor Module

**Files:**
- Create: `system_encryptor/` (entire directory from ToolNeuron2)
- Modify: `settings.gradle.kts`

**Step 1: Copy the module**

```bash
cp -r /home/home/AndroidStudioProjects/ToolNeuron2/system_encryptor /home/home/AndroidStudioProjects/ToolNeuron/system_encryptor
```

**Step 2: Register in settings.gradle.kts**

In `/home/home/AndroidStudioProjects/ToolNeuron/settings.gradle.kts`, add after `include(":neuron-packet")`:

```kotlin
include(":system_encryptor")
```

**Step 3: Verify module is recognized**

Run: `cd /home/home/AndroidStudioProjects/ToolNeuron && ./gradlew :system_encryptor:tasks --quiet`
Expected: Task list prints without errors

**Step 4: Commit**

```bash
git add system_encryptor/ settings.gradle.kts
git commit -m "feat: port system_encryptor module from ToolNeuron2"
```

---

### Task 1.2: Copy file_ops Module

**Files:**
- Create: `file_ops/` (entire directory from ToolNeuron2)
- Modify: `settings.gradle.kts`

**Step 1: Copy the module**

```bash
cp -r /home/home/AndroidStudioProjects/ToolNeuron2/file_ops /home/home/AndroidStudioProjects/ToolNeuron/file_ops
```

**Step 2: Register in settings.gradle.kts**

Add after the system_encryptor include:

```kotlin
include(":file_ops")
```

**Step 3: Verify module is recognized**

Run: `cd /home/home/AndroidStudioProjects/ToolNeuron && ./gradlew :file_ops:tasks --quiet`
Expected: Task list prints without errors

**Step 4: Commit**

```bash
git add file_ops/ settings.gradle.kts
git commit -m "feat: port file_ops module from ToolNeuron2"
```

---

### Task 1.3: Copy ums Module

**Files:**
- Create: `ums/` (entire directory from ToolNeuron2)
- Modify: `settings.gradle.kts`

**Step 1: Copy the module**

```bash
cp -r /home/home/AndroidStudioProjects/ToolNeuron2/ums /home/home/AndroidStudioProjects/ToolNeuron/ums
```

**Step 2: Register in settings.gradle.kts**

Add after the file_ops include:

```kotlin
include(":ums")
```

**Step 3: Verify module is recognized**

Run: `cd /home/home/AndroidStudioProjects/ToolNeuron && ./gradlew :ums:tasks --quiet`
Expected: Task list prints without errors

**Step 4: Commit**

```bash
git add ums/ settings.gradle.kts
git commit -m "feat: port ums module from ToolNeuron2"
```

---

### Task 1.4: Add Plaintext Mode to UMS C++ Layer

**Files:**
- Modify: `ums/src/main/cpp/ums.cpp`
- Modify: `ums/src/main/java/com/dark/ums/UnifiedMemorySystem.kt`

**Step 1: Add FLAG_PLAINTEXT_MODE constant**

In `UnifiedMemorySystem.kt` companion object, add:

```kotlin
const val FLAG_PLAINTEXT_MODE = 0x0002
```

**Step 2: Add plaintext create/open JNI methods**

In `UnifiedMemorySystem.kt`, add new methods:

```kotlin
/** Create UMS in plaintext mode (no encryption, no passphrase). */
fun createPlaintext(basePath: String): Boolean {
    return nativeCreatePlaintext(basePath)
}

/** Open UMS in plaintext mode. */
fun openPlaintext(basePath: String): Boolean {
    return nativeOpenPlaintext(basePath)
}

private external fun nativeCreatePlaintext(basePath: String): Boolean
private external fun nativeOpenPlaintext(basePath: String): Boolean
```

**Step 3: Implement in ums.cpp**

Add C++ JNI functions that:
- Create manifest with `FLAG_PLAINTEXT_MODE` set
- Skip PBKDF2 key derivation
- Skip CryptoEngine encrypt/decrypt for records
- Still use LZ4 compression, WAL, and indexes
- Use dummy zero-filled DEK (no actual encryption)

The implementation needs to:
1. Check `FLAG_PLAINTEXT_MODE` in manifest flags on `open()`
2. If set, bypass all encrypt/decrypt calls in `Collection::put()` and `Collection::get()`
3. Store raw (possibly LZ4-compressed) record data directly

**Step 4: Build and verify**

Run: `cd /home/home/AndroidStudioProjects/ToolNeuron && ./gradlew :ums:assembleDebug`
Expected: Build succeeds, `libums.so` generated

**Step 5: Commit**

```bash
git add ums/
git commit -m "feat: add plaintext mode to UMS (FLAG_PLAINTEXT_MODE)"
```

---

### Task 1.5: Wire App Dependencies

**Files:**
- Modify: `app/build.gradle.kts`

**Step 1: Add module dependencies**

In `app/build.gradle.kts` dependencies block, add:

```kotlin
implementation(project(":system_encryptor"))
implementation(project(":file_ops"))
implementation(project(":ums"))
```

**Step 2: Verify full build**

Run: `cd /home/home/AndroidStudioProjects/ToolNeuron && ./gradlew :app:assembleDebug`
Expected: Build succeeds, all native libs load

**Step 3: Commit**

```bash
git add app/build.gradle.kts
git commit -m "feat: wire system_encryptor, file_ops, ums dependencies in app"
```

---

## Phase 2: Onboarding UI

### Task 2.1: Copy ActionCelebrationProgress Component

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/components/ActionComponents.kt`

**Step 1: Copy ActionCelebrationProgress composable**

From ToolNeuron2's `ActionComponents.kt` (lines 814-923), copy the `ActionCelebrationProgress` composable function into ToolNeuron's `ActionComponents.kt`.

This includes:
- The particle data class
- The particle system LaunchedEffect
- The Canvas drawing code
- The animated progress bar

Ensure all imports are added (Canvas, Animatable, spring, etc.).

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`
Expected: No errors

**Step 3: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/components/ActionComponents.kt
git commit -m "feat: add ActionCelebrationProgress particle effect component"
```

---

### Task 2.2: Create IntroScreen

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/screen/IntroScreen.kt`

**Step 1: Copy and adapt IntroScreen**

Copy from ToolNeuron2's `IntroScreen.kt` (132 lines). Adapt:
- Package: `com.dark.tool_neuron.ui.screen`
- Replace `InferenceClient.runtimeSetup` with a local initialization flow
- Keep: permission request, title/tagline fade-in, ActionCelebrationProgress
- The `onFinished()` callback navigates to ShowcaseScreen or VaultGateScreen

**Step 2: Verify compilation**

Run: `./gradlew :app:compileDebugKotlin`

**Step 3: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/IntroScreen.kt
git commit -m "feat: add IntroScreen with runtime init and celebration progress"
```

---

### Task 2.3: Create ShowcaseScreen

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/screen/ShowcaseScreen.kt`

**Step 1: Copy and adapt ShowcaseScreen**

Copy from ToolNeuron2's `ShowcaseScreen.kt` (494 lines). Adapt:
- Package: `com.dark.tool_neuron.ui.screen`
- Update icon references to use ToolNeuron's `TnIcons` / `TnAnimIcons`
- Keep: 4-page pager, morphing blob background, page indicator dots
- `onFinished()` callback navigates to VaultGateScreen

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/ShowcaseScreen.kt
git commit -m "feat: add ShowcaseScreen with 4-page onboarding carousel"
```

---

### Task 2.4: Create VaultGateScreen + ViewModel

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/screen/VaultGateScreen.kt`
- Create: `app/src/main/java/com/dark/tool_neuron/viewmodel/VaultGateViewModel.kt`

**Step 1: Copy and adapt VaultGateViewModel**

Copy from ToolNeuron2's `VaultGateViewModel.kt` (176 lines). Adapt:
- Package: `com.dark.tool_neuron.viewmodel`
- Add `SecurityMode` enum: `REGULAR`, `PROTECTED`
- Add `setSecurityMode(mode: SecurityMode)` method
- In `submit()`:
  - If REGULAR: call `ums.createPlaintext(basePath)` or `ums.openPlaintext(basePath)`
  - If PROTECTED: call `ums.createWithPassphrase(basePath, appKey, passphrase)`
- Keep: migration detection, 4-phase migration, retry logic

**VaultGateState sealed interface:**
```kotlin
sealed interface VaultGateState {
    data object SecuritySelection : VaultGateState  // NEW - pick regular/protected
    data object Setup : VaultGateState              // passphrase setup (protected only)
    data object Unlock : VaultGateState             // passphrase entry (returning protected user)
    data object Deriving : VaultGateState           // key derivation in progress
    data class Migrating(
        val phase: Int, val phaseName: String,
        val current: Int, val total: Int,
        val logs: List<String>
    ) : VaultGateState
    data class MigrationComplete(
        val migrated: Int, val skipped: Int, val failures: List<String>
    ) : VaultGateState
    data class Error(val message: String, val isMigration: Boolean = false) : VaultGateState
}
```

**Step 2: Copy and adapt VaultGateScreen**

Copy from ToolNeuron2's `VaultGateScreen.kt` (434 lines). Adapt:
- Package: `com.dark.tool_neuron.ui.screen`
- Add `SecuritySelection` composable showing Regular/Protected cards
- Keep: passphrase input, strength indicator, migration progress, migration summary
- Use ToolNeuron's `ActionTextField`, `ActionButton`, etc.

**Step 3: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/ui/screen/VaultGateScreen.kt \
        app/src/main/java/com/dark/tool_neuron/viewmodel/VaultGateViewModel.kt
git commit -m "feat: add VaultGateScreen with security mode selection and migration UI"
```

---

### Task 2.5: Wire Navigation Flow

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/activity/MainActivity.kt`

**Step 1: Add new routes**

Add to Screen sealed class:

```kotlin
object Intro : Screen("intro")
object Showcase : Screen("showcase")
object VaultGate : Screen("vault_gate")
```

**Step 2: Update startDestination logic**

```kotlin
val startDestination = when {
    !umsExists -> Screen.Intro.route          // First launch or upgrade
    isProtectedMode -> Screen.VaultGate.route  // Protected user needs unlock
    else -> Screen.Chat.route                  // Regular user, straight to chat
}
```

Where:
- `umsExists` = check if UMS directory exists on disk
- `isProtectedMode` = DataStore preference `SECURITY_MODE == "protected"`

**Step 3: Add composable routes**

```kotlin
composable(Screen.Intro.route) {
    IntroScreen(onFinished = {
        val target = if (showcaseSeen) Screen.VaultGate.route else Screen.Showcase.route
        navController.navigate(target) { popUpTo(Screen.Intro.route) { inclusive = true } }
    })
}
composable(Screen.Showcase.route) {
    ShowcaseScreen(onFinished = {
        navController.navigate(Screen.VaultGate.route) { popUpTo(Screen.Showcase.route) { inclusive = true } }
    })
}
composable(Screen.VaultGate.route) {
    VaultGateScreen(onVaultReady = {
        navController.navigate(Screen.Chat.route) { popUpTo(Screen.VaultGate.route) { inclusive = true } }
    })
}
```

**Step 4: Use auto-triggered transitions (crossfade 400ms) for onboarding flow**

**Step 5: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/activity/MainActivity.kt
git commit -m "feat: wire Intro → Showcase → VaultGate → Home navigation"
```

---

## Phase 3: UMS Repositories

### Task 3.1: Define Tag Constants

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/TagConstants.kt`

**Step 1: Create tag constant file**

```kotlin
package com.dark.tool_neuron.data.ums

/** UMS wire format tag constants for each collection. */
object Tags {
    // ── Models ──
    object Model {
        const val MODEL_NAME = 1
        const val MODEL_PATH = 2
        const val PATH_TYPE = 3    // VARINT: 0=FILE, 1=CONTENT_URI
        const val PROVIDER_TYPE = 4 // VARINT: 0=GGUF, 1=DIFFUSION, 2=TTS
        const val FILE_SIZE = 5    // FIXED64
        const val IS_ACTIVE = 6    // VARINT: 0/1
    }

    // ── Model Config ──
    object Config {
        const val MODEL_ID = 1
        const val LOADING_PARAMS = 2  // BYTES (JSON)
        const val INFERENCE_PARAMS = 3 // BYTES (JSON)
    }

    // ── Personas ──
    object Persona {
        const val NAME = 1
        const val AVATAR = 2
        const val SYSTEM_PROMPT = 3
        const val GREETING = 4
        const val IS_DEFAULT = 5
        const val CREATED_AT = 6       // FIXED64
        const val DESCRIPTION = 7
        const val PERSONALITY = 8
        const val SCENARIO = 9
        const val EXAMPLE_MESSAGES = 10
        const val ALTERNATE_GREETINGS = 11 // BYTES (JSON array)
        const val TAGS = 12               // BYTES (JSON array)
        const val AVATAR_URI = 13
        const val CREATOR_NOTES = 14
        const val SAMPLING_PROFILE = 15   // BYTES (JSON)
        const val CONTROL_VECTORS = 16    // BYTES (JSON)
    }

    // ── AI Memories ──
    object Memory {
        const val FACT = 1
        const val CATEGORY = 2       // VARINT
        const val SOURCE_CHAT_ID = 3
        const val CREATED_AT = 4     // FIXED64
        const val UPDATED_AT = 5     // FIXED64
        const val LAST_ACCESSED_AT = 6 // FIXED64
        const val ACCESS_COUNT = 7   // VARINT
        const val EMBEDDING = 8      // BYTES
        const val IS_SUMMARIZED = 9  // VARINT: 0/1
        const val SUMMARY_GROUP_ID = 10
        const val PERSONA_ID = 11
    }

    // ── Knowledge Entities ──
    object KgEntity {
        const val NAME = 1
        const val TYPE = 2           // VARINT
        const val EMBEDDING = 3      // BYTES
        const val FIRST_SEEN = 4     // FIXED64
        const val LAST_SEEN = 5      // FIXED64
        const val MENTION_COUNT = 6  // VARINT
    }

    // ── Knowledge Relations ──
    object KgRelation {
        const val SUBJECT_ID = 1     // VARINT (entity record ID)
        const val OBJECT_ID = 2      // VARINT
        const val PREDICATE = 3
        const val CONFIDENCE = 4     // FIXED32 (float)
        const val SOURCE_FACT_ID = 5
        const val CREATED_AT = 6     // FIXED64
        const val PERSONA_ID = 7
    }

    // ── Chats ──
    object Chat {
        const val CHAT_ID = 1
        const val CREATED_AT = 2     // FIXED64
        const val TITLE = 3
        const val PRIMARY_MODEL_ID = 4
        const val PRIMARY_PERSONA_ID = 5
        const val LAST_MESSAGE_AT = 6  // FIXED64
        const val MESSAGE_COUNT = 7    // VARINT
    }

    // ── Messages ──
    object Message {
        const val MSG_ID = 1
        const val CHAT_ID = 2
        const val ROLE = 3           // VARINT: 0=User, 1=Assistant
        const val CONTENT_TYPE = 4   // VARINT: 0=None,1=Text,2=Image,3=TextWithImage,4=PluginResult
        const val CONTENT = 5
        const val IMAGE_DATA = 6
        const val IMAGE_PROMPT = 7
        const val IMAGE_SEED = 8     // FIXED64
        const val TIMESTAMP = 9      // FIXED64
        const val MODEL_ID = 10
        const val PERSONA_ID = 11
        const val DECODING_METRICS = 12  // BYTES (JSON)
        const val IMAGE_METRICS = 13     // BYTES (JSON)
        const val MEMORY_METRICS = 14    // BYTES (JSON)
        const val RAG_RESULTS = 15       // BYTES (JSON)
        const val PLUGIN_METRICS = 16    // BYTES (JSON)
        const val TOOL_CHAIN_STEPS = 17  // BYTES (JSON)
        const val AGENT_PLAN = 18
        const val AGENT_SUMMARY = 19
        const val PLUGIN_RESULT_DATA = 20 // BYTES (JSON)
    }

    // ── Collection Names ──
    const val COLLECTION_MODELS = "models"
    const val COLLECTION_CONFIGS = "model_config"
    const val COLLECTION_PERSONAS = "personas"
    const val COLLECTION_MEMORIES = "memories"
    const val COLLECTION_KG_ENTITIES = "knowledge_entities"
    const val COLLECTION_KG_RELATIONS = "knowledge_relations"
    const val COLLECTION_CHATS = "chats"
    const val COLLECTION_MESSAGES = "messages"
}
```

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/data/ums/TagConstants.kt
git commit -m "feat: define UMS tag constants for all collections"
```

---

### Task 3.2: Build UmsModelRepository

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/UmsModelRepository.kt`

**Step 1: Write the repository**

Implements same interface as ModelDao but backed by UMS. Key methods:
- `suspend fun insert(model: Model)` → `ums.put("models", model.toRecord())`
- `suspend fun getById(id: String): Model?` → `ums.get("models", idInt)?.toModel()`
- `fun getAll(): Flow<List<Model>>` → wrap `ums.getAll("models")` in a flow
- `suspend fun getAllOnce(): List<Model>` → `ums.getAll("models").map { it.toModel() }`
- `suspend fun updateActiveStatus(id: String, isActive: Boolean)` → get, modify, put

Include `Model.toRecord()` and `UmsRecord.toModel()` extension functions using `Tags.Model.*`.

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/data/ums/UmsModelRepository.kt
git commit -m "feat: add UmsModelRepository (replaces ModelDao)"
```

---

### Task 3.3: Build UmsConfigRepository

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/UmsConfigRepository.kt`

Same pattern as UmsModelRepository. Key: query by modelId using UMS string index on tag `Config.MODEL_ID`.

**Commit:** `"feat: add UmsConfigRepository (replaces ModelConfigDao)"`

---

### Task 3.4: Build UmsPersonaRepository

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/UmsPersonaRepository.kt`

Handles TavernAI v2 fields. `alternateGreetings` and `tags` stored as JSON arrays (BYTES).

**Commit:** `"feat: add UmsPersonaRepository (replaces PersonaDao)"`

---

### Task 3.5: Build UmsMemoryRepository

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/UmsMemoryRepository.kt`

Handles embedding BLOBs, per-persona queries, summarization grouping.
Index on `Tags.Memory.PERSONA_ID` for per-persona isolation.

**Commit:** `"feat: add UmsMemoryRepository (replaces AiMemoryDao)"`

---

### Task 3.6: Build UmsKnowledgeRepository

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/UmsKnowledgeRepository.kt`

Combines entity + relation operations. Index on entity name, relation subject_id/object_id.

**Commit:** `"feat: add UmsKnowledgeRepository (replaces KnowledgeEntityDao + KnowledgeRelationDao)"`

---

### Task 3.7: Build UmsChatRepository

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/UmsChatRepository.kt`

This is the largest repository — replaces VaultHelper entirely. Key features:

- `createChat()` → `ums.put("chats", chatRecord)`
- `addMessage(chatId, message)` → `ums.put("messages", messageRecord)` + update chat's lastMessageAt/messageCount
- `getMessagesForChat(chatId)` → `ums.queryString("messages", Tags.Message.CHAT_ID, chatId)`
- `getMessagesForChatPaged(chatId, fromTime, toTime, limit)` → range query on timestamp
- `getAllChats()` → `ums.getAll("chats")` sorted by lastMessageAt
- `deleteChat(chatId)` → delete chat record + all messages with that chatId
- `searchMessages(query)` → linear scan with content.contains(query) (or future FTS)
- `exportChat(chatId)` → serialize to JSON for backup
- `importChat(export)` → deserialize and insert

Must create indexes on initialization:
```kotlin
fun ensureIndexes() {
    ums.addIndex(Tags.COLLECTION_MESSAGES, Tags.Message.CHAT_ID, UnifiedMemorySystem.WIRE_BYTES)
    ums.addIndex(Tags.COLLECTION_MESSAGES, Tags.Message.TIMESTAMP, UnifiedMemorySystem.WIRE_FIXED64)
    ums.addIndex(Tags.COLLECTION_CHATS, Tags.Chat.LAST_MESSAGE_AT, UnifiedMemorySystem.WIRE_FIXED64)
}
```

**Commit:** `"feat: add UmsChatRepository (replaces VaultHelper)"`

---

## Phase 4: Migration Engine

### Task 4.1: Create ToolNeuron Migration Engine

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/worker/UmsMigrationEngine.kt`

**Step 1: Write migration engine**

Adapts ToolNeuron2's `MigrationEngine.kt` for ToolNeuron's specific schema (Room v12 + VaultHelper).

```kotlin
class UmsMigrationEngine(
    private val context: Context,
    private val ums: UnifiedMemorySystem,
    private val onProgress: (phase: Int, phaseName: String, current: Int, total: Int, log: String) -> Unit
) {
    fun needsMigration(): Boolean {
        // Check: Room DB file exists AND FLAG_MIGRATION_COMPLETE not set
        val dbFile = context.getDatabasePath("tool_neuron_database")
        return dbFile.exists() && (ums.getFlags() and UnifiedMemorySystem.FLAG_MIGRATION_COMPLETE) == 0
    }

    suspend fun run(): MigrationResult {
        // Phase 1: Auto-backup
        createAutoBackup()

        // Phase 2: Room DB → UMS
        migrateRoomData()

        // Phase 3: VaultHelper → UMS
        migrateVaultData()

        // Phase 4: Verify
        verify()

        return result
    }
}
```

**Phase 2 detail (Room → UMS):**
- Open Room DB read-only via `AppDatabase.getDatabase(context)`
- For each DAO: query all rows, convert to UmsRecord, put into UMS
- Use existing entity classes for deserialization
- Close Room DB after reading

**Phase 3 detail (Vault → UMS):**
- Initialize VaultHelper (read-only)
- Get all chats via `VaultHelper.getAllChats()`
- For each chat: get all messages, convert to UMS records
- Add `modelId = null` and `personaId = null` for all legacy messages
- Close VaultHelper after reading

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/worker/UmsMigrationEngine.kt
git commit -m "feat: add UmsMigrationEngine (Room + VaultHelper → UMS)"
```

---

### Task 4.2: Create VaultManager Singleton

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/data/ums/VaultManager.kt`

```kotlin
object VaultManager {
    private var _ums: UnifiedMemorySystem? = null
    val ums: UnifiedMemorySystem get() = _ums ?: error("UMS not initialized")

    fun init(umsInstance: UnifiedMemorySystem) {
        _ums = umsInstance
    }

    fun isReady(): Boolean = _ums != null

    fun close() {
        _ums?.close()
        _ums = null
    }
}
```

**Commit:** `"feat: add VaultManager singleton for UMS lifecycle"`

---

## Phase 5: Wire & Cleanup

### Task 5.1: Update AppContainer to Use UMS

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/di/AppContainer.kt`

**Step 1: Replace Room-based providers with UMS-based providers**

- Remove `getDatabase()`, `getPersonaDao()`, `getAiMemoryDao()`
- Add `getUmsModelRepository()`, `getUmsPersonaRepository()`, etc.
- Remove VaultHelper initialization (replaced by VaultManager + UMS)
- Keep DataStore references

**Step 2: Commit**

```bash
git add app/src/main/java/com/dark/tool_neuron/di/AppContainer.kt
git commit -m "refactor: update AppContainer to use UMS repositories"
```

---

### Task 5.2: Update ChatManager to Use UmsChatRepository

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/worker/ChatManager.kt`

Replace all `VaultHelper.*` calls with `UmsChatRepository.*` calls. The public interface stays the same — `Result<T>` wrapping, same method signatures.

**Key changes:**
- `VaultHelper.addMessage(chatId, message)` → `chatRepo.addMessage(chatId, message)`
- `VaultHelper.getMessagesForChat(chatId)` → `chatRepo.getMessagesForChat(chatId)`
- `VaultHelper.getAllChats()` → `chatRepo.getAllChats()`
- Remove `withVaultReady {}` wrapper (UMS is always ready after init)

**Commit:** `"refactor: update ChatManager to use UmsChatRepository"`

---

### Task 5.3: Update ChatViewModel

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/viewmodel/ChatViewModel.kt`

**Key changes:**
- Remove direct VaultHelper/Room references
- Ensure `chatManager` is the sole data access path
- When saving messages, include `modelId` and `personaId` from current active selections:

```kotlin
val message = Messages(
    // ... existing fields ...
).also { msg ->
    // NEW: modelId and personaId tracked per-message
    currentModelId = appSettings.lastModelId.first()
    currentPersonaId = appSettings.activePersonaId.first()
}
```

**Critical perf fix while here:** Replace `assistantContent += token` (line ~1440) with StringBuilder:

```kotlin
private val responseBuilder = StringBuilder()
// In token callback:
responseBuilder.append(token)
_streamingAssistantMessage.value = responseBuilder.toString()
```

**Commit:** `"refactor: update ChatViewModel for UMS + fix StringBuilder perf"`

---

### Task 5.4: Update All DAO Consumers

**Files to modify (search for Room DAO imports):**
- `app/.../worker/MemoryExtractor.kt` — uses AiMemoryDao
- `app/.../worker/KnowledgeGraphBuilder.kt` — uses KnowledgeEntityDao, KnowledgeRelationDao
- `app/.../worker/ControlVectorManager.kt` — uses PersonaDao
- `app/.../worker/PersonaCleanupHelper.kt` — uses PersonaDao
- `app/.../viewmodel/PersonaEditorViewModel.kt` or similar — uses PersonaDao
- `app/.../viewmodel/LLMModelViewModel.kt` — uses ModelDao, ModelConfigDao
- `app/.../viewmodel/PluginViewModel.kt` — uses imports from ai_gguf
- `app/.../ui/screen/PersonaEditorScreen.kt` — uses PersonaDao
- `app/.../ui/screen/AiMemoryScreen.kt` — uses AiMemoryDao

For each: replace `dao.method()` with `umsRepo.method()`. The method signatures are compatible by design.

**Commit per file group or batch commit:** `"refactor: migrate DAO consumers to UMS repositories"`

---

### Task 5.5: Update SystemBackupManager

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/worker/SystemBackupManager.kt`

**Key changes:**
- Backup: copy UMS directory instead of Room DB file + VaultHelper file
- New entry type: `UMS_DIRECTORY`
- Remove `DB_FILE` and `VAULT_FILE` entry types (keep for v2 restore compat)
- Portable chat export: read from UMS instead of VaultHelper
- On restore: close UMS → restore UMS directory → reopen UMS

**Commit:** `"refactor: update SystemBackupManager for UMS storage"`

---

### Task 5.6: Update DataIntegrityManager

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/worker/DataIntegrityManager.kt`

**Key changes:**
- Replace Room DAO calls with UMS repository calls
- Keep same checks: dangling refs, missing models, orphaned avatars, memory pruning
- Add new check: UMS collection consistency (record count > 0 for expected collections)

**Commit:** `"refactor: update DataIntegrityManager for UMS"`

---

### Task 5.7: Remove Room DB + VaultHelper

**Files to delete:**
- `app/src/main/java/com/dark/tool_neuron/database/AppDatabase.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/ModelDao.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/ModelConfigDao.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/PersonaDao.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/AiMemoryDao.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/KnowledgeEntityDao.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/KnowledgeRelationDao.kt`
- `app/src/main/java/com/dark/tool_neuron/database/dao/RagDao.kt`
- `app/src/main/java/com/dark/tool_neuron/models/converters/Converters.kt`
- `app/src/main/java/com/dark/tool_neuron/vault/VaultHelper.kt`
- `app/src/main/java/com/dark/tool_neuron/vault/VaultExtensions.kt`

**Files to modify:**
- `app/build.gradle.kts` — remove Room dependencies (`room-ktx`, `room-runtime`, `room-compiler`)
- `app/build.gradle.kts` — remove `implementation(project(":memory-vault"))`
- `settings.gradle.kts` — remove `include(":memory-vault")`

**DO NOT delete yet:**
- Room entity files in `models/table_schema/` — still needed by migration engine to read old data
- `memory-vault/` module directory — keep until migration is proven stable (1-2 releases)

**Commit:** `"refactor: remove Room DB and VaultHelper (migration-only remnants kept)"`

---

## Phase 6: Performance Fixes

### Task 6.1: Fix Critical Performance Issues

**Files:**
- Modify: `app/.../viewmodel/ChatViewModel.kt`
  - Replace `assistantContent += token` with `StringBuilder` (O(n) → O(1) append)
  - Replace `messages.clear(); messages.addAll(newList)` with single state update
  - Fix repetition detection to use sliding window instead of full string scan

- The N+1 query in `getAllChats()` is already fixed by UmsChatRepository (single `getAll("chats")` call)

- The O(n) memory dedup in `MemoryExtractor.kt` is improved by UMS index on personaId

**Commit:** `"perf: fix critical token streaming, recomposition, and query bottlenecks"`

---

### Task 6.2: Fix High Priority Issues

**Files:**
- Modify: `app/.../viewmodel/ChatViewModel.kt`
  - Audit all `viewModelScope.launch {}` blocks for proper cancellation
  - Store Job references and cancel in `onCleared()`

**Commit:** `"perf: fix leaked coroutine jobs in ChatViewModel"`

---

### Task 6.3: Add Security Mode to DataStore

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/data/AppSettingsDataStore.kt`

Add:
```kotlin
val SECURITY_MODE = stringPreferencesKey("security_mode") // "regular" or "protected"
val SHOWCASE_SEEN = booleanPreferencesKey("showcase_seen")
```

**Commit:** `"feat: add security mode and showcase seen preferences"`

---

## Execution Notes

### Build Verification After Each Phase

After Phase 1: `./gradlew :app:assembleDebug` — all native libs compile
After Phase 2: `./gradlew :app:compileDebugKotlin` — UI compiles
After Phase 3: `./gradlew :app:compileDebugKotlin` — repos compile
After Phase 5: `./gradlew :app:assembleDebug` — full build works
After Phase 6: Full app test on device

### Testing Checklist

- [ ] Fresh install: Intro → Showcase → SecuritySelection → Home
- [ ] Fresh install (protected): Intro → Showcase → Passphrase → Home
- [ ] Upgrade (existing user): Intro → SecuritySelection → Migration → Home
- [ ] Migration preserves all models, configs, personas
- [ ] Migration preserves all chat messages (content, timestamps, metrics)
- [ ] Legacy messages have modelId=null (no crash)
- [ ] New messages include modelId + personaId
- [ ] Protected mode: passphrase required on every app open
- [ ] Regular mode: straight to chat (no passphrase)
- [ ] Backup/restore works with UMS format
- [ ] NeuronPacket RAGs still work (unchanged)
- [ ] Chat performance: no token streaming lag
- [ ] Chat list loads in <100ms (was N+1 query, now single getAll)

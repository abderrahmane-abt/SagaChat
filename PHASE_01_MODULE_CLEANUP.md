# Phase 01 — Module & Build Cleanup

Strip every module and build dependency that has zero relevance to a
roleplay app. Goal: clean build with only the inference core, encrypted
storage, download pipeline, and the app shell.

---

## 1. Delete entire module directories

From the repo root, delete these folders completely:

```
native-server/
plugin-api/
plugin-exc/
plugins/
```

Command:
```bash
rm -rf native-server plugin-api plugin-exc plugins
```

---

## 2. `settings.gradle.kts` — remove module includes

Open `settings.gradle.kts`. Remove every line that includes a deleted module.

Lines to remove:
```kotlin
include(":native-server")
include(":plugin-api")
include(":plugin-exc")
include(":plugins:notes")
include(":plugins:counter")
include(":plugins:expense")
```

Keep all remaining includes unchanged.

---

## 3. `app/build.gradle.kts` — remove dead dependencies

Open `app/build.gradle.kts`. Remove these dependency declarations:

```kotlin
implementation(project(":native-server"))
implementation(project(":plugin-api"))
implementation(project(":plugin-exc"))
```

Also remove any dependency line that references:
- `ai_sd` (Stable Diffusion AAR) — find lines like:
  ```kotlin
  implementation(files("../libs/ai_sd-release.aar"))
  // or
  implementation(files("../libs/ai_sd-debug.aar"))
  ```
- `sherpa` or `ai_sherpa` (voice AAR) — lines like:
  ```kotlin
  implementation(files("../libs/ai_sherpa-release.aar"))
  ```

Keep:
```kotlin
implementation(files("../libs/gguf_lib-release.aar"))   // inference engine — keep
implementation(project(":hxs"))                          // keep
implementation(project(":hxs_encryptor"))               // keep
implementation(project(":download_manager"))            // keep
implementation(project(":networking"))                  // keep
```

---

## 4. `app/build.gradle.kts` — remove dead proguard consumer rules

If there is a `proguardFiles` or `consumerProguardFiles` block referencing
`ai_sd`, `sherpa`, or plugin modules, remove those lines.

---

## 5. Verification

Run:
```bash
./gradlew :app:compileDebugKotlin
```

Expected: build fails due to unresolved imports (voice, plugin, RAG, server,
image gen classes used in screens and viewmodels). That is correct — the screen
and viewmodel cleanup happens in Phase 02. At this point you only need the
module graph to resolve cleanly (no `project(":plugin-api")` not found errors).

If you see `Module not found: :native-server` style errors, check
`settings.gradle.kts` again.

---

## What stays after this phase

| Module | Status |
|--------|--------|
| `:app` | Keep |
| `:hxs` | Keep |
| `:hxs_encryptor` | Keep |
| `:download_manager` | Keep |
| `:networking` | Keep |
| `gguf_lib-release.aar` | Keep |
| `:native-server` | **Deleted** |
| `:plugin-api` | **Deleted** |
| `:plugin-exc` | **Deleted** |
| `:plugins:*` | **Deleted** |
| `ai_sd-*.aar` | **Removed from deps** (file can stay on disk) |
| `ai_sherpa-release.aar` | **Removed from deps** (file can stay on disk) |

# ToolNeuron — Repo Guide

Project memory for this repo. **When you change anything that affects future work — architecture, security behavior, new features, deprecated paths, public APIs, native JNI contracts, new scope — update this file as part of the same change.** A future session reads this to reconstruct intent; if it drifts, work breaks.

---

## Project Scope

Privacy-first, offline-only on-device AI assistant. No Google Play services, no network telemetry, no analytics. In-scope pillars: RAG over user documents, vision-language models (VLM), a private in-app browser. Out of scope (pivoted 2026-04-20): tool calling, image generation, plugin hub, Termux integration (reverted in commit `34149eb`).

Package: `com.dark.tool_neuron` · minSdk 29 · targetSdk 36 · abiFilters `arm64-v8a`, `x86_64`.

Modules:
- `:app` — UI (Jetpack Compose), viewmodels, DI graph, activities, services.
- `:hxs` — "HexStorage", proprietary encrypted key-value store (Kotlin wrapper + C++ core).
- `:hxs_encryptor` — crypto + integrity primitives: Argon2id, AES-GCM/ChaCha20-Poly1305, BoringSSL, ML-KEM-768, ML-DSA-65, Ed25519, HKDF, mmap+mlock `SecureBuffer`, plus the native security policy / auth / boot-integrity stack.
- `:download_manager`, `:networking` — ancillary modules.

---

## Build & Release

- **Dev build (debug):** `./gradlew :app:compileDebugKotlin` to verify compilation. `./gradlew :app:installDebug` to install on a connected device. Never `assemble+adb install` — use Gradle tasks.
- **Release build:** built **from Android Studio** (not the command line). The signing plumbing in `app/build.gradle.kts` reads from `local.properties`; if all four keys are present Android Studio uses them when creating a release APK/AAB:
  ```
  TN_KEYSTORE_PATH=/path/to/your/keystore
  TN_KEYSTORE_PASSWORD=...
  TN_KEY_ALIAS=...
  TN_KEY_PASSWORD=...
  ```
  If any of those are missing, release falls back to being unsigned so dev flow isn't blocked.
- **Native build:** `./gradlew :hxs_encryptor:externalNativeBuildDebug` — BoringSSL + liboqs fetched via CMake `FetchContent`. The LSP frequently flags `'openssl/mem.h' file not found` / `'jni.h' file not found` / `CRYPTO_memcmp undeclared` — these are false positives; the CMake build resolves them. Build-green is the source of truth, not clangd.
- **Instrumented tests:** `./gradlew :app:connectedDebugAndroidTest` against a connected emulator or device.

---

## Hard rules for code in this repo

1. **HXS-only persisted storage.** No `SharedPreferences`, no Room, no DataStore, no raw files. The only exception is the XOR-masked raw binary file at `app_bootstrap/k.bin` that holds the Keystore-wrapped DEK — it has to live outside the encrypted vault by construction, and the old plaintext HXS bootstrap was replaced with a raw file to remove HXS container metadata leakage on disk.
2. **Security logic lives in C++/JNI.** Kotlin wraps native; every authentication / trust / policy decision is made in native code and crosses JNI as an opaque token or bool. No boolean-trust-through-JNI, no Kotlin `if (verify)` gating.
3. **No comments in source** except one-liner `//` for non-obvious WHY. No decorative banners, no block comments, no docstrings. Names and structure must be self-documenting. This file is the documentation.
4. **Never write fully-qualified class names inline.** Always `import` at the top; use the short name in the body.
5. **ViewModels live under `ui/viewmodel/`** (the one the codebase uses is actually `com.dark.tool_neuron.viewmodel`). Never co-locate a VM with its screen.
6. **Commit hygiene:** conventional commits, no `Co-Authored-By` trailer, never push without explicit ask, never skip hooks. **Don't commit unless the user explicitly asks.**
7. **Research / exploration subagents run on `sonnet` at low effort** — not Opus — unless the user overrides.
8. **No TODOs, stubs, or half-implementations.** Every task is coded end-to-end.
9. **When you change security-affecting state, update this CLAUDE.md in the same change.**
10. **One Scaffold only** — the root `AppScaffold`. Screens must NOT wrap themselves in their own `Scaffold`. They receive `innerPadding: PaddingValues`, apply it to their content root, and render a plain `Column`/`LazyColumn`/`Box`. Per-route top bars go in `AppTopBar.kt`'s `when`; per-route bottom bars go in `AppBottomBar.kt`'s `when`. If a new screen needs a title bar, create a small `XxxTopBar()` composable and dispatch to it from `AppTopBar`. Same pattern for bottom bars.
11. **Library modules must NOT minify.** `:hxs`, `:hxs_encryptor`, `:download_manager`, `:networking`, `:rag-doc-lib` all ship `isMinifyEnabled = false`. Only `:app` minifies. Pre-minified library classes (`a/a`, `a/b`, …) collide with prebuilt third-party jars like `gguf_lib-release-runtime.jar` that already ship minified — R8 fails with `Type a.a is defined multiple times`. Keep rules live in each module's `consumer-rules.pro`.

---

## Security architecture

### The five bypasses that were closed (audit, 2026-04-22)

Before hardening, a blue-team + red-team + external-research Opus audit found five script-kiddie full-bypasses:

1. **Plaintext HXS app_prefs vault** (`AppPreferences.kt` used `createPlaintext`/`openPlaintext`) — password_hash, salt, security_mode sat on disk as plaintext wire bytes with no MAC.
2. **Boolean verify crossed JNI** — `verifyPassword()` returned a `Boolean` the Kotlin side trusted.
3. **Kotlin `if (verifyPassword)` guard** on `disableLock()` — attacker could flip one branch and kill the lock.
4. **Argon2id t=2 / m=16MiB / p=2** — too weak for offline brute force.
5. **`runAntiTamperChecks()` + `nativeVerifyApkSignature` existed but had zero call sites.**

Plus `OPENSSL_NO_ASM=1` had disabled ARM crypto instructions, `allowBackup="true"` leaked via adb backup, and `TempActivity` + `ColorShowcaseActivity` were `android:exported="true"` with no filters.

All closed. 49/49 instrumented tests pass on Pixel_Tablet AVD API 35.

### Layered architecture

```
┌──────────────────────────────────────────────────────────────┐
│ UI (Compose)                                                 │
│  PasswordScreen / SetupPasswordScreen wrapped in SecureScreen│
│  AppScaffold watches shouldLock → re-routes to PasswordScreen│
└──────────────────────────────────────────────────────────────┘
            │ state flows via ScaffoldViewModel
┌──────────────────────────────────────────────────────────────┐
│ App Kotlin layer                                             │
│  SecurityManager — the only auth API the app consumes        │
│  SessionHolder  — holds the opaque 32-byte token             │
│  AppLockObserver — ProcessLifecycleOwner, clears on ON_STOP  │
│  NativeIntegrity — TOFU .so hashes + APK signer capture      │
│  AccessibilityGuard, RootGuard, PinStrength                  │
│  AppPreferences  — encrypted HXS + sealed AuthState blob     │
│  AppKeyStore     — Android Keystore wrap/unwrap of DEK       │
└──────────────────────────────────────────────────────────────┘
            │ opaque tokens, sealed blobs, hash arrays
┌──────────────────────────────────────────────────────────────┐
│ Native (libhxs_encryptor.so)                                 │
│  PolicyEngine  — single is_allowed(feature_id, token) gate   │
│  AuthNative    — Argon2id setup/verify, emits session token  │
│  BootIntegrity — JNI_OnLoad hooks, hook-baseline capture     │
│  IntegrityGuard — debugger/frida/xposed/sig/hash primitives  │
│  CryptoEngine  — AEAD, HKDF, Argon2id, Ed25519, X25519       │
│  HybridKem/HybridSign — X25519+ML-KEM-768, Ed25519+ML-DSA-65 │
└──────────────────────────────────────────────────────────────┘
```

### Keystore DEK flow (bootstrap)

1. `AppKeyStore` reads/writes a raw XOR-masked binary file at `filesDir/app_bootstrap/k.bin`. Payload layout is `[magic "TNDK"(4)][version(1)=1][iv_len(2)][iv][ct_len(2)][ct]`, masked byte-wise against a 32-byte hardcoded XOR key. No HXS container, no collection names, no field tags — just the wrapped DEK. XOR is obfuscation (not security); the cryptographic protection is the Keystore-wrapped ciphertext inside.
2. Android Keystore key — alias `toolneuron_vault_dek_v1`, AES-256-GCM, `setIsStrongBoxBacked(true)` with `StrongBoxUnavailableException` fallback to TEE. Not `setUserAuthenticationRequired(true)` yet (chicken-and-egg with setup flow).
3. First launch: generate 32-byte DEK via `SecureRandom`, wrap with Keystore AES-GCM, write XOR-masked blob to `k.bin`.
4. Every launch: read `k.bin`, XOR-unmask, parse `{iv, ct}`, unwrap via `Cipher.getInstance("AES/GCM/NoPadding")` + `GCMParameterSpec(128, iv)`.
5. `AppKeyStore.backing()` classifies the key as `STRONGBOX` / `TEE` / `SOFTWARE_FALLBACK` / `UNKNOWN` via `KeyInfo.securityLevel` (API 31+) or `KeyInfo.isInsideSecureHardware` (older). Use this to gate pro-tier features against soft-only fallbacks later.
6. `AppKeyStore.wipe()` deletes `k.bin` AND the Keystore alias — invoked by `SecurityManager.hardWipe()`.
7. **Legacy migration:** on startup, if `k.bin` doesn't exist but the `app_bootstrap/` directory has any other file (legacy plaintext HXS format), everything in `app_bootstrap/` and `app_prefs/` is wiped plus the Keystore alias is cleared — old encrypted `app_prefs/` can't be decrypted by the fresh DEK anyway. Existing users returning from the legacy format go through onboarding again.

### AppPreferences — encrypted HXS + sealed auth blob

- Opens `filesDir/app_prefs/` via `HexStorage.openEncrypted(path, appKey=DEK, userKey=HKDF(DEK, info="tn.app_prefs.user_key.v1"), encryptor)`. Second layer on auth-critical state: `writeAuthState`/`readAuthState` AEAD-encrypt/decrypt the `AuthState` blob with key `HKDF(DEK, info="tn.app_prefs.auth_key.v1")` and AAD `"tn.auth_state.v1"`. Gives us defense in depth plus the hook for a Phase-3-later monotonic-counter anti-rollback AAD.
- Ordinary flags (`onboarding_complete`, `tc_accepted`, `setup_done`, `security_setup_done`, `model_setup_done`, `guide_shown`) are plain encrypted records. Only auth-critical state (security_mode, salt, hash, panic salt/hash, lockout counter, last-seen-now) rides the sealed blob.

### AuthState layout (v4 — current)

```
version(1) = 4
security_mode(1)             — 0=NONE, 1=APP_PASSWORD
salt_len(2) + salt
hash_len(2) + hash
failed_attempts(2)
next_attempt_at_ms(8)
has_panic(1)
  if has_panic:
    panic_salt_len(2) + panic_salt
    panic_hash_len(2) + panic_hash
last_seen_now_ms(8)          — monotonic wall clock anchor
```

Decoder accepts v1/v2/v3/v4 and zero-fills missing fields. Always bump the version constant when you change the layout AND extend the decoder.

### Native auth path

- `hxs::auth::setup(pin) → {salt[16], hash[32]}` — Argon2id `t=4 / m=128MiB (131072 KiB) / p=1 / outLen=32`.
- `hxs::auth::verify(pin, salt, stored_hash) → 32-byte session_token | null`. On match: constant-time `CRYPTO_memcmp`, random token via `CryptoEngine::random_bytes`, calls `hxs::policy::register_session(token)`.
- `hxs::auth::invalidate()` → `hxs::policy::invalidate_session()`.
- Kotlin uses `AuthNative` (object) to cross JNI. The token is opaque bytes; Kotlin never sees any hash.

### PolicyEngine — the single gate

Every gated capability calls `PolicyEngine.isAllowed(Feature, sessionToken)`. Native `hxs::policy::is_allowed(feature_id, token, len)` logic:

1. If `tampered` → return false unconditionally (latched one-way).
2. If `is_pro_feature(fid)` (i.e. fid ≥ 1000) → **return false**. *(This is the flip point for the future license system — swap this branch to "verify signed license blob".)*
3. If `is_unauth_feature(fid)` (APP_LAUNCH, OPEN_VAULT, AUTH_SETUP, AUTH_VERIFY, UI_PASSWORD_SCREEN, UI_SETUP_SCREEN, UI_INTRO) → return true.
4. If `passthrough` is on (no-lock mode set when `security_mode == NONE`) → return true.
5. Else require `session_active && CRYPTO_memcmp(stored_token, given_token, 32) == 0`.

State mutations: `register_session`, `invalidate_session`, `set_passthrough`, `mark_tampered`, `reset_for_testing` (test-only; wipes the whole state).

Feature IDs are declared in `hxs_encryptor/src/main/cpp/policy_engine.h` and mirrored as `PolicyEngine.Feature` in Kotlin. Keep them in sync.

### Lockout / backoff / wipe / clock rollback

`LockoutPolicy.backoffMillis(failedAttempts)` — 0 for first 3 tries, 1m → 5m → 15m → 1h → 4h → 12h → 24h. `WIPE_THRESHOLD = 10` hits `SecurityManager.hardWipe()`.

Clock-rollback defense: `AuthState.lastSeenNowMs` tracked on every verify. If `nowMs + CLOCK_SKEW_GRACE_MS (5 min) < lastSeenNowMs`, the attempt is penalized as if it had rolled the clock backward — failed-attempts bumps by 2, backoff extends from `max(nowMs, lastSeenNowMs)`. Prevents "set phone clock to 1970 to bypass backoff".

`hardWipe()` sequence: `session.clear()` → `PolicyEngine.invalidateSession()` → `PolicyEngine.markTampered()` → `prefs.clearAuthState()` → `keyStore.wipe()` (deletes Keystore alias + bootstrap vault). Next launch boots into fresh setup flow.

Panic PIN: `SecurityManager.setPanicPin(pin)` (requires active session) writes a second Argon2id hash into AuthState. `verifyPassword` tries the real hash first; on mismatch, if `hasPanic` is set, tries the panic hash. Panic match → `hardWipe()` + `VerifyResult.Wiped` (indistinguishable UX from "attempts exceeded").

Min PIN is 6 digits; the UI also clamps max to 6 (PIN pad refuses a 7th digit, `PinDotRow` always renders exactly 6 dots). Weak PINs (all-same, monotonic sequence ±1, top-20 commons like `121212`/`112233`) are rejected at setup via `PinStrength.evaluate`.

### Boot integrity pipeline

`JNI_OnLoad` in `hxs_encryptor.cpp`:
1. `hxs::boot::install_ptrace_self_trace()` — PTRACE_TRACEME (if any debugger is attached, we notice later via `TracerPid`).
2. `hxs::boot::capture_hook_baselines()` — captures first 32 bytes of `hxs::auth::verify`, `hxs::policy::is_allowed`, `hxs::boot::hard_fail`.

`TNApplication.onCreate()` runs these sequentially:
1. `integrity.scanProcessEnvironment()` — `is_debugger_attached` + `is_frida_present` + `is_xposed_present`. Any positive → `BootIntegrity.hardFail(reasons)`.
2. `integrity.bootVerify()` — TOFU .so hash walk, rebound to install identity. Manifest layout v2: `version(1) + signerHashLen(2)+signerHash + versionCode(8) + lastUpdateTime(8) + count(4) + (nameLen(2)+filename + hashLen(2)+hash)*`. On every launch we compute `{signerHash, longVersionCode, lastUpdateTime}` from `PackageManager`; if any field differs from the stored manifest (or no manifest exists), we re-TOFU against current `.so` files and store. Only within the same install identity do we enforce the filename-set + hash match — mismatch → `FAIL_LIB_HASH` → hard fail. Filenames are stored (not absolute paths) since Android reshuffles the random token in `/data/app/~~…/` on every reinstall. `apk_signer_hash_v1` is also written for the future license-binding path.
3. `BootIntegrity.verifyHookBaselines()` — re-reads the 32-byte prologue of critical functions and compares to the baseline captured at load. Catches inline hooks from Frida/substrate.
4. `accessibilityGuard.scan()` — reads `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES`. Anything outside the stock-Android + OEM allowlist → release hard-fails, debug warns.
5. `appLockObserver.register()` — registers the lifecycle observer that clears the session on `ON_STOP`.

Root detection was removed from the boot path (no hard-fail). `RootGuard.scan()` now runs from `ScaffoldViewModel` on first launch; if rooted and `AppPreferences.rootWarningShown == false`, `AppScaffold` shows a one-time `RootWarningDialog` informing the user that other root-capable apps could read ToolNeuron's on-disk state. Acknowledging the dialog flips `rootWarningShown = true`, never shown again. `RootGuard` is still available for later threat-model refinement (e.g., opportunistic feature gating on rooted devices).

`BootIntegrity.hardFail(reason)` → `PolicyEngine.markTampered()` + `_exit(1)` unless `setRelaxedForTesting(true)` is active (tests flip the flag to inspect tamper behavior without killing the process).

### Tamper / hook obfuscation

All detection strings in `integrity.cpp` are XOR-obfuscated via the `HXS_OBF(var, "literal")` macro in `hxs_encryptor/src/main/cpp/xor_str.h`. Verified: `strings libhxs_encryptor.so | grep -iE '^(frida|gadget|linjector|xposed|TracerPid)$'` returns zero matches. Future hardening: per-build random XOR key.

### Session lifecycle + UI lock

- `SessionHolder` exposes `active: StateFlow<Boolean>`. Flips true on `AuthNative.verify` success, false on `clear()`.
- `AppLockObserver` (a `DefaultLifecycleObserver` on `ProcessLifecycleOwner`) calls `session.clear()` on `ON_STOP` when `security.isLockEnabled`. Session-clear also invokes `AuthNative.invalidate()` which clears the native session.
- `ScaffoldViewModel.shouldLock` = `security.isLockEnabled && !session.active`. `AppScaffold` observes it; when true it navigates to `PasswordScreen` with `popUpTo(0) { inclusive = true }` — except when we're already on a screen that should not be interrupted (`PasswordScreen`, `SetupScreen`, `IntroScreen`).

### FLAG_SECURE

`ui/components/SecureScreen.kt` — a composable that adds `WindowManager.LayoutParams.FLAG_SECURE` on enter and clears on dispose. Applied to `PasswordScreen` and `SetupPasswordScreen` (PIN entry). Not applied globally so users can screenshot their own chat content.

### SecureClipboard

`util/SecureClipboard.kt`. Sets `ClipDescription.EXTRA_IS_SENSITIVE` on TIRAMISU+ (keeps content out of clipboard-history surfaces). Schedules an auto-clear after 30 s via `SupervisorJob() + Dispatchers.Main` — clear only fires if the clip still matches what we wrote (doesn't clobber a later user copy).

### Release build hardening

`app/build.gradle.kts`:
- `isMinifyEnabled = true`, `isShrinkResources = true`.
- `isDebuggable = false`, `isJniDebuggable = false`.
- Proguard (`app/proguard-rules.pro`) strips `Log.d/v/i` via `assumenosideeffects` (keeps `w` and `e` for production telemetry). `-repackageclasses ''` + `-allowaccessmodification` on top of default optimizations.

---

## Future pro license system — the hook

**The license plumbing is already live:** every gated feature routes through `PolicyEngine.isAllowed(Feature, sessionToken)`. Feature IDs `≥ 1000` are `PRO_*` and currently return false in community builds. When you're ready to monetize:

1. Flip the `is_pro_feature` branch in `hxs_encryptor/src/main/cpp/policy_engine.cpp` from `return false` to "verify signed license blob".
2. The license blob layout (planned): `{device_id_hash, features_bitmap, expiry_unix, nonce, signature}` — Ed25519 or ML-DSA-65 signed with the dev's private key; public key is XOR-baked into native at build time.
3. The APK signer SHA-256 is **already captured** on first launch and stored in HXS key `apk_signer_hash_v1`. That's the anchor the license binds against.
4. Device id must be an attested Keystore key fingerprint (hardware-rooted), **NOT** `Settings.Secure.ANDROID_ID` (resettable, clonable).
5. License blob itself lives in a separate HXS collection sealed under the same Keystore DEK.
6. On tamper detection (any of the existing checks), invalidate any loaded license too — single fail-closed path.

See also: `~/.claude-personal/projects/-home-home-AndroidStudioProjects-ToolNeuron-New/memory/project_pro_license_system.md`.

---

## File map — what lives where

### Native (`hxs_encryptor/src/main/cpp/`)
- `policy_engine.{h,cpp}` — `is_allowed`, session registry, tamper latch, `reset_for_testing`.
- `auth.{h,cpp}` — `setup(pin)`, `verify(pin, salt, hash)`, Argon2id hardened params.
- `boot_integrity.{h,cpp}` — env scan, lib-hash verification, hook-baseline capture/verify, `hard_fail`, `setRelaxedForTesting`.
- `integrity.{h,cpp}` — debugger/frida/xposed checks, file hashing, APK sig compare.
- `xor_str.h` — compile-time XOR macro `HXS_OBF(var, "literal")` for detection strings.
- `crypto_engine.{h,cpp}` — AEAD (AES-256-GCM / ChaCha20-Poly1305 auto), HKDF, PBKDF2, Argon2id, Ed25519, X25519, SHA-256.
- `memory_guard.{h,cpp}` — `SecureBuffer` (mmap+mlock+mprotect), `secure_zero`, `secure_compare`.
- `pq_kem.{h,cpp}` — X25519 + ML-KEM-768 hybrid KEM.
- `pq_sign.{h,cpp}` — Ed25519 + ML-DSA-65 hybrid signatures.
- `hxs_encryptor.cpp` — all JNI bindings + `JNI_OnLoad`.
- `CMakeLists.txt` — fetches BoringSSL + liboqs; `-march=armv8-a+crypto+sha2` on arm64; LTO/gc-sections/icf on release.

### Encryptor module Kotlin (`hxs_encryptor/src/main/java/com/dark/hxs_encryptor/`)
- `HxsEncryptor.kt` — crypto + integrity surface.
- `PolicyEngine.kt` — feature gate object (mirrors native `FeatureId` enum).
- `AuthNative.kt` — setup/verify/invalidate.
- `BootIntegrity.kt` — hook & lib verification, hashFile, hardFail, relaxed-for-testing toggle.

### App-side security (`app/src/main/java/com/dark/tool_neuron/data/`)
- `AppKeyStore.kt` — Keystore-wrapped DEK, `backing()` attestation.
- `SessionHolder.kt` — current token + `StateFlow<Boolean>`.
- `SecurityManager.kt` — app-facing auth API (setPassword/verifyPassword/disableLock/setPanicPin/hardWipe/setupWithoutLock).
- `SecurityModule.kt` — Hilt provider for `HxsEncryptor`.
- `AppPreferences.kt` — encrypted HXS vault + sealed AuthState.
- `AuthState.kt` — serialized lockout + panic state (v4 layout).
- `VerifyResult.kt` — sealed `Success | WrongPin | LockedOut(retryAtMs) | Wiped | NoLock`.
- `LockoutPolicy` (in `VerifyResult.kt`) — backoff schedule + wipe threshold.
- `NativeIntegrity.kt` — TOFU .so hash manifest + APK signer capture.
- `AppLockObserver.kt` — lifecycle observer for auto-lock.
- `AccessibilityGuard.kt` — allowlist of known-safe a11y packages.
- `RootGuard.kt` — su paths, test-keys, Magisk, root-manager packages.
- `PinStrength.kt` — weak-PIN rejection.

### UI pieces
- `ui/components/SecureScreen.kt` — FLAG_SECURE wrapper composable.
- `ui/screens/password_screen/PasswordScreen.kt` — wrapped in `SecureScreen`.
- `ui/screens/setup_screen/SetupPasswordScreen.kt` — wrapped in `SecureScreen`.
- `ui/screens/setup_screen/SetupThemeScreen.kt` — theme mode + palette chooser; part of first-run setup.
- `ui/screens/system_ui/AppScaffold.kt` — auto-lock re-routing via `ScaffoldViewModel.shouldLock`.
- `util/SecureClipboard.kt` — IS_SENSITIVE + auto-clear.
- `ui/screens/guide/` — one hub + seven detail screens + `GuideDetailLayout` + `GuideTopBar` (shared top bar dispatched from `AppTopBar`). See "App Guide — rewritten" above.
- `ui/screens/setup_screen/SetupThemeBottomBar.kt` — pinned Continue button for the SetupTheme route, dispatched from `AppBottomBar`.
- `ui/screens/home_screen/PlusMenu.kt` — four-item Plus menu (Documents / Thinking / Attach image / Load projector).
- `ui/screens/home_screen/HomeScreenBottomBar.kt` — hosts `PendingImageRow`, `VlmErrorBanner`, `DocumentChipsRow`, image + projector pickers.
- `ui/screens/model_store/RepositorySettings.kt::VlmProjectorCard` — Model Store Settings tab card for mmproj load/release.

### Build
- `app/build.gradle.kts` — release signingConfigs via `local.properties`, proguard wired, `isDebuggable=false`/`isJniDebuggable=false`.
- `app/proguard-rules.pro` — log strip, repackage, Hilt/serialization/native keeps.
- `hxs_encryptor/build.gradle.kts` — `OPENSSL_NO_ASM=1` removed.
- `gradle/libs.versions.toml` — `androidx-lifecycle-process` dependency added for `ProcessLifecycleOwner`.
- `app/src/main/AndroidManifest.xml` — `allowBackup=false`; `TempActivity` + `ColorShowcaseActivity` un-exported; `InferenceService` runs in `:inference` process.

---

## VLM (Vision-Language Models) pipeline

Vision inference rides on top of the active GGUF chat model via a separate mmproj projector file. The pipeline crosses the `:inference` process boundary, so images can't just be passed as byte arrays over AIDL (1 MB binder transaction limit). Instead images flow as `ParcelFileDescriptor[]`.

### AIDL contract

`app/src/main/aidl/com/dark/tool_neuron/service/IInferenceService.aidl`:
```
boolean loadVlmProjector(String path, int threads);
boolean loadVlmProjectorFromFd(in ParcelFileDescriptor pfd, int threads);
void releaseVlmProjector();
boolean isVlmLoaded();
String getVlmInfo();
String getVlmDefaultMarker();
void generateVlm(String messagesJson, in ParcelFileDescriptor[] imageFds, int maxTokens, IGenerationCallback callback);
```

### Service flow

`InferenceService.generateVlm(messagesJson, imageFds, maxTokens, cb)`:
1. For each PFD, opens an `AutoCloseInputStream` and `readBytes()` → `List<ByteArray>`.
2. Hands to native via `engine.generateVlmFlow(messagesJson, images, maxTokens)`.
3. Any read failure → `callback.onError(...)` and return; don't start generation.
4. `collectFlow(...)` bridges `GenerationEvent` → `IGenerationCallback`.

### Client side

`InferenceClient` (app process):
- `isVlmLoaded: StateFlow<Boolean>` — mirrors service-side state, polled on connect and via `onServiceDisconnected` / `onBindingDied`.
- `suspend loadVlmProjectorFromUri(context, uri, threads=2): Boolean` — opens `ParcelFileDescriptor`, calls `loadVlmProjectorFromFd`, updates state flow.
- `suspend releaseVlmProjector()` — native release + state flow false.
- `getVlmDefaultMarker(): String` — pull the model's image-marker token (e.g. `<image>`) from native.
- `generateVlm(context, messagesJson, imageUris, maxTokens): Flow<InferenceEvent>` — opens PFDs for each URI, passes array across binder, closes PFDs after `svc.generateVlm` returns. `callbackFlow` + `buffer(Channel.UNLIMITED)`.

### Coordinator routing

`InferenceCoordinator.run(...)` per iteration:
1. Grab last user message from `chatRepo.getMessages(chatId)`.
2. If iteration==0 AND last user has non-empty `imageUris` AND `InferenceClient.isVlmLoaded.value` → VLM route.
3. VLM route: `buildMessagesJson(messages, vlmLastUserId=lastUser.id)` prepends `getVlmDefaultMarker()` to the last user message's content (`"<image>\n<user text>"` if non-blank, else just the marker).
4. Call `InferenceClient.generateVlm(...)` instead of `generateMultiTurn(...)`.
5. Stream is collected identically.

Non-VLM path is untouched.

### UI

- Home Plus menu (`PlusMenu.kt`): four items — Documents / Thinking / Attach image / Load projector. "Load projector" cycles to "VLM on" when loaded — tapping it again releases. Image attach uses `ActivityResultContracts.PickVisualMedia.ImageOnly`. Projector picker uses `OpenDocument()` with `arrayOf("*/*")` (mmproj files don't have consistent MIME types).
- `HomeScreenBottomBar.kt`: `PendingImageRow` renders thumbnails above the input via `BitmapFactory.decodeStream`, no third-party image lib. `VlmErrorBanner` shows projector load failures with a dismiss X.
- `MessageBubble.kt`: `UserBubble` now conditionally renders `UserImageThumbnails` above the text bubble when `message.imageUris.isNotEmpty()`. Text bubble itself only draws if `content.isNotBlank()` (image-only messages are valid).
- `RepositorySettings.kt::VlmProjectorCard`: Model Store Settings tab hosts a VLM projector card showing status + Load/Replace/Release buttons. Disabled when no base model is loaded.

### HomeViewModel state

- `pendingImages: StateFlow<List<Uri>>` — images queued for the next send.
- `isVlmLoaded: StateFlow<Boolean>` — mirrors `InferenceClient.isVlmLoaded`.
- `isLoadingProjector: StateFlow<Boolean>` — spinner while native load runs.
- `vlmError: StateFlow<String?>` — projector-load errors.
- `addImage(uri)` / `removeImage(uri)` / `clearPendingImages()`.
- `loadVlmProjector(uri)` / `releaseVlmProjector()` / `clearVlmError()`.
- `sendMessage(text)` now accepts empty text if `pendingImages` is non-empty (image-only turn). Persists `imageUris = pendingImages.map { it.toString() }` and `kind = MessageKind.Image`. Clears `pendingImages` after persist.

### Persistence

`ChatMessage.imageUris: List<String>` persists to HXS via `ChatRepository.TAG_MSG_IMAGES = 8` (JSON array of URI strings). No image bytes ever land on disk here — we store the content:// URI and rehydrate via the content resolver.

### DI changes

- `HomeViewModel` now takes `Application` via Hilt (for `contentResolver.takePersistableUriPermission` + passing to `InferenceClient.generateVlm`).
- `InferenceCoordinator` now takes `Application` (same reason — it calls `InferenceClient.generateVlm(app, ...)`).

---

## Setup flow — theme step

Setup sequence is now: Intro → SetupScreen (lock mode) → (SetupPassword if password chosen) → **SetupTheme** → ModelSetup → Home.

- Route: `NavScreens.SetupTheme("setup_theme")`.
- Screen: `ui/screens/setup_screen/SetupThemeScreen.kt`. VM: `viewmodel/SetupThemeViewModel.kt` (injects `ThemeController`). Selecting a mode or palette hits the controller immediately — no submit step. The screen is a plain `Column`; it does **not** create its own `Scaffold`.
- **Continue button** lives in `SetupThemeBottomBar.kt` and is dispatched from `AppBottomBar.kt` for the `SetupTheme` route. The `onContinue` callback is wired in `AppScaffold.AppBottomBar(...)` and navigates to `ModelSetup` with `popUpTo(SetupTheme) { inclusive = true }`.
- Top bar is `SetupScreenTopBar()`, dispatched from `AppTopBar` on `SetupTheme.route`.
- AppScaffold handoff: `onSetupComplete → SetupTheme` (via TNavigation); `SetupTheme → ModelSetup` (via AppBottomBar). `onThemeSetupComplete` is no longer a `TNavigation` parameter — it's a direct AppBottomBar callback.
- No separate "theme setup done" flag — `ThemeController` defaults are valid on first launch so skipping this screen is harmless. If you want gating, add a `themeSetupDone` pref to `AppPreferences` and branch on it in `ScaffoldViewModel.nextDestination`.

---

## App Guide — rewritten (2026-04-23)

Single-file alternating-row guide was replaced by a hub-of-options model. **All guide screens follow the single-Scaffold rule**: no inner Scaffold/TopAppBar, they accept `innerPadding: PaddingValues` and render plain content.

- Hub: `ui/screens/guide/AppGuideScreen.kt` — `AppGuideScreen(innerPadding, onOpenEntry)`. Three categories ("Getting started" / "Advanced AI" / "Your phone, your data") with option cards. Each card dispatches to a detail route via `onOpenEntry(key)` where key ∈ `GuideEntryKeys`.
- Detail helper: `guide/GuideDetailLayout.kt` — `GuideDetailLayout(innerPadding, lede, icon, steps: List<GuideStep>, tips)`. Step cards are numbered, can embed an optional visual composable (`GuideStep.visual`). No `title` / `onBack` parameters — the top bar is owned by `AppTopBar`.
- Top bar: `guide/GuideTopBar.kt` — `GuideTopBar(title, onBack)`. `AppTopBar.kt` dispatches each guide route (`AppGuide`, `GuideChat`, `GuideModels`, `GuideRag`, `GuideVlm`, `GuideVoice`, `GuideSecurity`, `GuideThemes`) to a `GuideTopBar(title = "…", onBack = onBack)` with the title hardcoded per route. `onBack` is the `navController.popBackStack()` lambda that `AppScaffold` already passes into `AppTopBar`.
- Seven detail screens, one file each; all take `innerPadding: PaddingValues`:
  - `GuideChatScreen.kt` → `NavScreens.GuideChat` — mock bottom-bar leaf pill, chat bubbles + metric pills, thinking toggle mock, edit/regen/delete action row, stop button.
  - `GuideModelsScreen.kt` → `NavScreens.GuideModels` — selected-state filter chips, HF repo search + Add button, BYOM import card, 3-row sampler slider mocks (Temp/TopP/RepPenalty), Tiny & Fast preset card.
  - `GuideRagScreen.kt` → `NavScreens.GuideRag` — embedding-model card with Install button, Plus→Documents flow mock, ingest progress bar, doc chips with dismissible X.
  - `GuideVlmScreen.kt` → `NavScreens.GuideVlm` — VLM base-model card, "VLM on" projector pill, Plus→Attach image flow mock, image+question bubble, Release projector button.
  - `GuideVoiceScreen.kt` → `NavScreens.GuideVoice` — STT/TTS model cards with tags, mic button with sound-wave arc, speaker playback bubble.
  - `GuideSecurityScreen.kt` → `NavScreens.GuideSecurity` — on-device shield pill, PIN dot row (6 dots, 4 filled), Panic PIN warning pill, backoff chips row, StrongBox badge, clipboard auto-clear notice.
  - `GuideThemesScreen.kt` → `NavScreens.GuideThemes` — mode segment control (System/Light/Dark), palette color strip, Settings→Appearance pointer.
- TNavigation registers each with one-liner: `composable(NavScreens.GuideX.route) { GuideXScreen(innerPadding) }`.
- Adding a new feature: add a `GuideEntry` in `AppGuideScreen.guideCategories()`, a new `GuideEntryKeys.XXX` const, a route in `NavScreens`, a detail screen (accepting `innerPadding: PaddingValues`), a `composable(...)` registration in `TNavigation`, and a dispatch case in `AppTopBar.kt` to `GuideTopBar(title = "…", onBack = onBack)`.

---

## Test layout (`app/src/androidTest/java/com/dark/tool_neuron/`)

49 instrumented tests across 7 classes. All green on Pixel_Tablet AVD API 35.

- `PhaseOneSecurityTest` (11) — PolicyEngine gating, auth setup/verify, DEK stability, vault-opens-encrypted, verify→disableLock.
- `PhaseTwoIntegrityTest` (7) — TOFU first-run + second-run, tampered-manifest detection, hardFail marks policy tampered, env scan, input validation.
- `PhaseThreeHardeningTest` (10) — lockout counter/backoff/wipe, auto-lock, obfuscation-in-binary assertion, AuthState v2 round-trip.
- `PhaseFourDepthTest` (8) — hook-baseline verify, a11y guard, panic PIN wipe + real PIN still works, AuthState v3.
- `ExtraHardeningTest` (7) — Keystore backing category, clipboard auto-clear, `PinStrength` for each weak-PIN bucket.
- `ResilienceTest` (5) — clock-rollback penalty, NTP-grace tolerance, root scan clean on emulator, lastSeen persistence, backoff baseline.
- `ExampleInstrumentedTest` (1).

Any test that mutates global native state must call `PolicyEngine.resetForTesting()` in `@Before` **and** `BootIntegrity.setRelaxedForTesting(true)` **before** any code path that can call `hardFail` (otherwise the process `_exit(1)`s mid-test).

---

## What's still deferred

- **Encrypt WAL** (`hxs/src/main/cpp/wal.cpp:132-144`) — plaintext even in encrypted mode. Real audit finding; needs HXS WAL format work.
- **Native cert pinning** — low priority; offline-only scope.
- **Play Integrity opt-in** — conflicts with privacy-first (Google Play Services dep).

These are documented; resurrect them when scope changes.

---

## Things NOT to regress

- Don't re-introduce `Settings.Secure.ANDROID_ID` anywhere — we rely on Keystore-attested identities.
- Don't take Argon2 params below `t=4 / m=131072 / p=1`. Constants are in `hxs_encryptor/src/main/cpp/auth.h`.
- Don't re-add `OPENSSL_NO_ASM=1` to the CMake args — ARM crypto matters for performance on real devices.
- Don't expand the unauthenticated feature set in `policy_engine.cpp::is_unauth_feature` without explicit threat-model review — every entry bypasses the session check.
- Don't remove `setRelaxedForTesting` wiring; tests rely on it.
- Don't emit plaintext detection strings in native code — wrap them in `HXS_OBF` (or whatever successor obfuscation lands later).
- Don't switch back to `verifyPassword(): Boolean`. The contract is `VerifyResult`.
- Don't route any gated feature around `PolicyEngine.isAllowed` — that's the single choke point monetization and tamper-hard-fail depend on.
- Don't collapse the Quick-Start quant preference list (`QUICK_START_QUANTS` in `TNavigation.kt`) to a naive "first match" resolver. HuggingFace repos publish BF16 / F16 / Q4 / Q5 / Q8 side-by-side and a `startsWith(repoId)` match returns whichever sorts first alphabetically — which is BF16 for LFM. The priority list (`Q4_K_M → Q4_K_S → Q4_0 → Q5_K_M → Q5_K_S → Q8_0`, then smallest-by-size fallback) is what keeps the "Tiny & Fast" card downloading an actually tiny file.
- Don't send VLM image bytes as `byte[]` across AIDL — stay on the `ParcelFileDescriptor[]` transport. Images often exceed the 1 MB binder transaction limit and would throw `TransactionTooLargeException` at runtime only, not compile time.
- Don't read images on the main thread in `InferenceService.generateVlm` — PFD reads happen inside the `scope.launch` (Dispatchers.IO) that `collectFlow` runs, since `generateVlmFlow` is collected there. If you refactor, keep `pfd.readBytes()` off the main thread.
- Don't silently drop the VLM marker prefix from the last user message when `isVlmLoaded`. `buildMessagesJson(messages, vlmLastUserId)` must prepend `InferenceClient.getVlmDefaultMarker()` or the model won't know an image is attached.
- Don't co-locate the VLM projector with a specific model in the catalog — projectors are runtime attachments to the active GGUF base model, not first-class catalog entries. The `VlmProjectorCard` in `RepositorySettings.kt` is intentionally a free-standing card that only works when a base model is loaded.
- Don't break the setup-flow handoff. `AppScaffold.onSetupComplete → SetupTheme`, `onThemeSetupComplete → ModelSetup`, `onModelSetupComplete → Home`. If you add a setup step, insert a new callback; don't fold it onto an existing one.
- Don't drop the 16 KB page-size linker flag. `-Wl,-z,max-page-size=16384` must be on `target_link_options` in *every* native CMakeLists we own: `hxs_encryptor/src/main/cpp/CMakeLists.txt` and `../AiSystems/ai_sherpa/src/main/cpp/CMakeLists.txt` (rebuilt AAR lives in `libs/`). Android 15+ / Play Store requires LOAD segments aligned at 0x4000 on arm64-v8a + x86_64. To verify: `unzip -p libs/ai_sherpa-release.aar jni/arm64-v8a/libai_sherpa.so > /tmp/s.so && readelf -l /tmp/s.so | awk '/LOAD/{getline;print $NF}'` → expect `0x4000`.
- Don't `secureWipe` a key ByteArray that was passed to `HexStorage.openEncrypted` / `createEncrypted`. `hxs.cpp` keeps a `NewGlobalRef` to that exact JVM ByteArray and hands it to every subsequent `nativeEncrypt` / `nativeDecrypt` callback as the key. Zeroing the array silently turns every AEAD op into a zero-key op — in-session reads still work via the in-memory record map, but on the next launch the same DEK derives a non-zero userKey and every on-disk record silently fails to decrypt (see `collection.cpp:140-141` which just skips records whose decrypt returns empty). Symptom: 800+ byte `app_prefs.hxs` on disk but `count=0` after restart; user sees the full Intro → Dev Notes → Setup flow every launch and their theme resets. Fix landed in `AppPreferences.init`: removed the `encryptor.secureWipe(userKey)` call.
- Don't eagerly construct `AppKeyStore` or `AppPreferences` from any process other than the main app process. `InferenceService` runs in `:inference` (declared in `AndroidManifest.xml`), and both processes used to call `TNApplication.onCreate()` → inject `NativeIntegrity` → construct `AppPreferences` → race on generating the wrapped DEK in `app_bootstrap/`. Whichever lost the race ended up with a DEK that couldn't unwrap on next launch. Fix: `TNApplication.isMainProcess()` early-returns on non-main processes, and every integrity/pref Hilt field is now `dagger.Lazy<T>` so no eager construction happens even if something else drags `TNApplication` to life in `:inference`.
- Don't wrap individual screens in their own `Scaffold`. There's exactly one Scaffold: `AppScaffold`. Screens receive `innerPadding: PaddingValues` and render plain content. New per-route top/bottom bars are added to `AppTopBar.kt` / `AppBottomBar.kt` `when` blocks. Every time someone adds an inner Scaffold, we get a double top bar at runtime *and* it breaks the global fullscreen/lock routing logic that depends on AppScaffold knowing the current route.
- Don't set `isMinifyEnabled = true` on any library module (`:hxs`, `:hxs_encryptor`, `:download_manager`, `:networking`, `:rag-doc-lib`). Only `:app` minifies. A minified library produces class names like `a/a.class`, which collide at the app's R8 step with prebuilt third-party jars (e.g. `libs/gguf_lib-release-runtime.jar`) that already ship minified. Symptom: `Type a.a is defined multiple times` during `:app:minifyReleaseWithR8`. Keep rules for each library stay in its `consumer-rules.pro` (applied by the app's R8, not the library's).
- Don't remove the per-step `visual` composables from the guide detail screens. They're the *point* — they show users what the real UI looks like (Plus menu, Projector pill, PIN dots, sampler sliders, backoff chips, palette swatches, etc.). If you redesign the real UI, update the corresponding guide visual in the same commit or the guide will lie about what the user sees.
- Don't key the TOFU `.so` manifest by absolute path. Android reshuffles the random token in `/data/app/~~…/` on every reinstall and on some OS updates, so absolute paths die. Store filenames only and let `NativeIntegrity.bootVerify()` re-resolve them against `context.applicationInfo.nativeLibraryDir` at verify time. Symptom of the regression: app crashes on first launch after any `installDebug` with `boot verify failed reasons=16` (`FAIL_LIB_HASH`).
- Don't verify the `.so` manifest across app updates without rebinding to install identity. The manifest header carries `{signerHash, longVersionCode, lastUpdateTime}`; any mismatch triggers a re-TOFU rather than a hard-fail. Legitimate Play/ADB updates change `versionCode` or `lastUpdateTime` and would otherwise look identical to tampering. The `.so` content equality check is only meaningful *within* the same install identity (runtime-swap detection).
- Don't re-add a root hard-fail. Root detection now lives in `ScaffoldViewModel.resolveInitialRootWarning()` and surfaces a one-time `RootWarningDialog` gated by `AppPreferences.rootWarningShown`. The user is trusted to make their own call on their own device. Rooted users were being silently `_exit(1)`'d in release builds and could not even see the PIN screen.
- Don't re-add a plaintext HXS container for the bootstrap DEK. `AppKeyStore` now writes a raw XOR-masked file at `app_bootstrap/k.bin` instead. The HXS container leaked tag names and collection metadata that weren't secret but were unnecessary on-disk signal; the current format is just `[magic][version][iv_len][iv][ct_len][ct]` masked byte-wise. The cryptographic protection is still the Keystore-wrapped ciphertext — XOR is obfuscation only.
- Don't skip the `migrateLegacyIfNeeded()` path in `AppKeyStore.init`. If `k.bin` doesn't exist but anything else lives in `app_bootstrap/`, we nuke `app_bootstrap/*` + `app_prefs/*` and clear the Keystore alias so the next user path is a clean re-bootstrap. Without this, users upgrading from the legacy HXS format get stuck with an unreadable encrypted `app_prefs/` vault forever.

---

## Housekeeping rule

Whenever you change anything on the list below, update **this file** as part of the same change:

- Security architecture or threat model
- Any auth flow or API surface (SecurityManager, SessionHolder, PolicyEngine, AuthNative, BootIntegrity)
- Any sealed state layout (AuthState, NativeIntegrity manifest, license blob)
- New feature IDs or reshuffling of the pro-feature range
- New persistent keys in HXS (`app_prefs` or `app_bootstrap`)
- New integrity checks, new obfuscation scheme, new crypto primitives
- New DI bindings that touch the security graph
- Changes to release-build hardening (proguard, signing, manifest flags)
- Anything marked here under "What's still deferred" moving in or out of scope
- Any new "Things NOT to regress" item discovered along the way

If the update to CLAUDE.md is not part of your diff, the change isn't finished.

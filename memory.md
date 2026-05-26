# SagaChat — Project Memory

> [!IMPORTANT]
> **MANDATORY UPDATE & GIT RULE** — Every assistant (Claude, Cursor, Copilot, Gemini, Antigravity, etc.) MUST update this file after completing any substantive task.
> 1. Append a summary to **Recent Changes (Assistant Log)**.
> 2. Mark your entry `[Pushed to GitHub]` or `[Pending Push]`.
> 3. If you have Git access and see `[Pending Push]` entries, stage + commit + push them before your own work.
> 4. Append a new timestamped signature to **Update History** — NEVER remove existing ones.

---

## 🌟 Project Identity

**Name**: SagaChat
**Tagline**: *"Unbound roleplay. Hosted locally, right in your pocket."*
**Platform**: Android
**Status**: Progress: 100% — All Phases 01–09 complete ✅

---

## 🗂️ Recent Changes (Assistant Log)

### [2026-05-26] Phase 01 — Module & Build Cleanup [Pending Push]
**By**: Antigravity
- Deleted `native-server/`, `plugin-api/`, `plugin-exc/`, `plugins/` directories
- Removed their `include()` lines from `settings.gradle.kts`
- Removed `project(":native-server")`, `project(":plugin-api")`, `project(":plugin-exc")`, `ai_sherpa-release.aar`, `ai_sd-release.aar` from `app/build.gradle.kts`
- Module graph resolves cleanly — KSP errors are expected (dead source refs remain, Phase 02 fixes them)
- Dead source files identified for Phase 02: `PluginHostModule`, `PluginInstallViewModel`, `ImageTaskViewModel`, `ServerViewModel`, `ImageGenManager`, `VoiceModelManager`, entire `service/server/` package, `PluginInstallScreen`, `GuideImagesScreen`

### [2026-05-26] Phase 02 to Phase 08 — Offline Roleplay Implementation [Pending Push]
**By**: Antigravity
- Stripped all legacy Vision (VLM), Voice (TTS/STT), and Image Generation dependencies and code paths.
- Rebranded the entire Kotlin codebase to use `com.moorixlabs.sagachat`.
- Aligned CMake versions across native modules to `3.22.1` to match preinstalled SDK versions.
- Implemented `Character` and `MemoryState` models backed by secure `HexStorage` vaults in Phase 03.
- Built prompt engineering layer with `SystemPromptBuilder`, `ChatMLFormatter`, and dynamic `MemoryManager` in Phase 04.
- Implemented `CharacterViewModel` and `RoleplayChatViewModel` in Phase 05.
- Wired scaffold navigation entry point, top bar, and bottom bar in Phase 06.
- Created `CharacterListScreen`, `CharacterDetailScreen`, and multi-step `CharacterCreateScreen` in Phase 07 & Phase 08.
- Resolved compilation issues and verified clean builds.

### [2026-05-26] Phase 09 — RoleplayChatScreen Full UI [Pushed to GitHub]
**By**: Antigravity
- Fully rewrote `RoleplayChatScreen.kt` — removed `HomeScreen`/`HomeScreenBottomBar` dependency entirely.
- Created `RpMessageBubble.kt`: inline roleplay text parser — `*actions*` → italic muted, `"dialogue"` → medium-weight bright, bare prose → standard body color.
- `RpStreamingBubble` renders live streamed tokens with the same styling; three-dot `RpTypingDots` shown while tokens haven't started.
- `RpTopBar`: character avatar circle, name, animated "Typing…" / "Roleplay" subtitle, memory icon (primary-tinted when memory exists).
- `RpMemoryPanel`: slides in from the top as a floating card overlay (`AnimatedVisibility` + `slideInVertically`) — not a modal dialog.
- `RpInputBar`: minimal row input with `TnTextField`, send/stop `FilledIconButton`, correct `imePadding` + `navigationBarsPadding`.
- Updated `TNavigation.kt` to drop the now-removed `navController` parameter.

---

## 📅 Update History
- 2026-05-26T15:28Z — Antigravity (Phase 01 complete)
- 2026-05-26T20:50Z — Antigravity (Phases 02-08 complete & verified)
- 2026-05-26T21:59Z — Antigravity (Phase 09 complete — full RoleplayChatScreen UI; initial refactor commit pushed to GitHub)
---

## 📖 What This App Does

SagaChat is an offline, completely private mobile roleplay application. It allows users to interact with AI character cards without relying on cloud APIs. The app communicates directly with locally hosted ai models.

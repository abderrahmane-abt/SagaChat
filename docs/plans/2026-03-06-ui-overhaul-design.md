# UI Overhaul Design

**Date:** 2026-03-06
**Status:** Approved
**Scope:** All UI files — icons, animations, responsive layout, M3 Expressive, file organization, dead code

## Problem

The UI codebase has accumulated technical debt:
- 244 Material icon references + 31 XML drawables alongside 52 TnIcons (tabler.io) — mixed icon systems, `material-icons-extended` adds ~2.5MB to APK
- 6+ different spring configs and 6+ tween durations — inconsistent animation feel across screens
- 1,459+ `rDp()`/`rSp()` calls — custom responsive scaling system not recommended by Google
- Oversized files: BodyContent.kt (1963 lines), ModelStoreScreen.kt (1959 lines), DynamicActionWindow.kt (1190 lines)
- Material3 pinned to `1.5.0-alpha15` outside BOM but Expressive features not fully utilized
- `PixelProgressBar` custom component instead of standard M3 indicators
- Persona UI screens still present but feature is unused
- Duplicated UI patterns (expand/collapse toggle in 8+ files, password toggle in 5+ files)

## Decisions

### 1. Icon System — TnIcons Only

- Replace all 244 Material icon references with TnIcons (tabler.io primary, Lucide fallback)
- Replace all 31 XML drawable references with TnIcons equivalents
- Delete XML drawable files from `res/drawable/` after migration
- Add ~24 new icons to TnIcons.kt (tabler paths) + `lucide()` helper for fallbacks
- Remove `material-icons-extended` dependency from build.gradle.kts
- Delete version pin from libs.versions.toml

### 2. Animation Standardization

- Create `ui/theme/Motion.kt` — delegates to `MotionScheme.expressive()` from M3 Expressive
- Replace all inline spring/tween specs with Motion tokens
- Standardize AnimatedVisibility patterns: entrance = fadeIn + expandVertically, exit = fadeOut + shrinkVertically
- All `animate*AsState` calls must have `label` parameter

### 3. Full M3 Expressive Adoption

Switch from `MaterialTheme` to `MaterialExpressiveTheme`:

| Component | Replaces | Usage |
|-----------|----------|-------|
| `MaterialExpressiveTheme` | `MaterialTheme` | Top-level theme wrapper |
| `MotionScheme.expressive()` | Custom animation specs | Built-in motion tokens |
| `LoadingIndicator` / `ContainedLoadingIndicator` | `CircularProgressIndicator` | Model loading, generation states |
| `HorizontalFloatingToolbar` | Custom bottom action bars | Message action bar (copy/TTS/regen) |
| `SplitButtonLayout` | Multi-action buttons | Send + attach, generation controls |
| `ButtonGroup` | Manual button rows | Filter chips, mode toggles |
| `FloatingActionButtonMenu` | Custom FAB popups | New chat + options |
| `FlexibleTopAppBar` | Current TopAppBar | All screens |

No bottom navigation bar — top app bar is the navigation anchor.

### 4. Progress Indicators

- Delete `PixelProgressBar` entirely
- Use standard `LinearProgressIndicator` with a custom particle celebration effect at the thumb
- Particle effect: tiny water droplet/sparkle particles flying up/down from progress edge as it moves
- Subtle but engaging — few particles, organic motion

### 5. Responsive Layout (replacing rDp/rSp)

Hybrid approach: `WindowSizeClass` for structure + fixed Material spacing scale.

Spacing constants in `ui/theme/Dimensions.kt`:
- XXS=2dp, XS=4dp, SM=8dp, MD=12dp, LG=16dp, XL=24dp, XXL=32dp, XXXL=48dp

WindowSizeClass breakpoints:
- Compact (<600dp): Single column, standard margins
- Medium (600-840dp): Optional side panels, wider margins
- Expanded (>840dp): Two-pane layouts (future tablet support)

Text sizing: Use `MaterialTheme.typography.*` tokens directly, no `rSp()`. System font scale handles accessibility.

Delete `rDp()` and `rSp()` functions from Theme.kt after migration.

### 6. Dependencies

- Keep `compose-bom = "2026.02.01"` (manages everything)
- Keep `material3 = "1.5.0-alpha15"` override (for Expressive APIs)
- Delete `material-icons-extended` dependency entirely
- Let BOM manage `compose-runtime` and `compose-ui-text` (remove version pins)

### 7. File Organization

Feature-based packages for screens, shared components stay in `components/`:

```
ui/
  screen/
    home/
      HomeScreen.kt, BodyContent.kt, HomeDrawerScreen.kt,
      DynamicActionWindow.kt, HomeTopBar.kt, MessageActions.kt,
      MetricsDisplay.kt, RagResultsSection.kt
    model_store/
      ModelStoreScreen.kt, ModelStoreFilters.kt,
      ModelStoreCard.kt, ModelDownloadSheet.kt
    model_config/
      ModelConfigEditorScreen.kt, ConfigSliders.kt
    memory/
      AiMemoryScreen.kt, TerminalLoggerScreen.kt,
      VaultDashboard.kt, VaultLoggerScreen.kt
    settings/
      SettingsScreen.kt, BackupSection.kt
    rag/
      SecureRagCreationScreen.kt, RagSourcePicker.kt
    files/
      ModelPickerScreen.kt
    setup/
      SetupScreen.kt
    gate/
      VaultGateScreen.kt
    guide/
      GuideScreen.kt, TermsAndConditionsScreen.kt
  components/
    ActionButtons.kt, StandardComponents.kt,
    MarkdownText.kt, MathRenderer.kt, SyntaxHighlight.kt,
    AgentExecutionView.kt, ToolChainUI.kt,
    CelebrationProgress.kt, ParticleProgress.kt,
    BottomSheets.kt, ModeToggleSwitch.kt,
    MemoryTimelineView.kt, MemoryResultsDisplay.kt,
    PluginResultDisplay.kt, RagExportDialog.kt,
    ExpandCollapseHeader.kt, PasswordToggleIcon.kt
  icons/
    TnIcons.kt
  theme/
    Theme.kt, Motion.kt, Dimensions.kt, Typography.kt, Color.kt
```

### 8. Persona UI Removal (UI-only)

- Delete `PersonaScreen.kt` and `PersonaEditorScreen.kt`
- Remove navigation routes to persona screens
- Remove persona section from SettingsScreen
- Backend (database, repositories, etc.) stays dormant — full removal is a separate task

### 9. Dead Code & Cleanup

- Delete `PixelProgress.kt`
- Delete `VaultManagementScreen.kt` (empty stub)
- Merge or delete `CuteSwitch.kt` if redundant after M3 Expressive adoption
- Extract duplicated patterns: `ExpandCollapseHeader` (8+ files), `PasswordToggleIcon` (5+ files)
- Single-line comments only throughout all modified files

## Scope Summary

| Category | Files Touched | Files Created | Files Deleted |
|----------|--------------|---------------|---------------|
| Icons | 35+ | 0 (expand TnIcons.kt) | 31 XML drawables |
| Animations | 40+ | 1 (Motion.kt) | 0 |
| M3 Expressive | 15+ | 0 | 1 (PixelProgress.kt) |
| Particle Progress | 0 | 1 (ParticleProgress.kt) | 0 |
| Responsive | All UI files | 1 (Dimensions.kt) | 0 |
| File org | ~15 moves | ~8 extractions | 0 |
| Persona UI | 4-5 | 0 | 2 |
| Dead code | 3-4 | 2 (shared patterns) | 3-4 |

**Total: ~50-60 files modified, ~10-12 created, ~8-10 deleted**

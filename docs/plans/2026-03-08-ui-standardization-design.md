# UI Standardization & File Splitting Design

**Date:** 2026-03-08
**Scope:** App module UI layer — no Gradle module changes

## Goal

Unify scattered UI design patterns into a single consistent design language (Style A), split 5 monster files (~7,300 lines) into ~28 focused files (~250 lines avg), and group ChatViewModel's 32 StateFlows into hot/warm/cold state objects for better recomposition performance.

## Phase 1: ChatViewModel State Grouping

Create 5 grouped state data classes by update frequency:

- **StreamingState** (HOT ~50ms): userMessage, assistantMessage, image, imageProgress, imageStep
- **ChatUiState** (WARM): isGenerating, currentChatId, error, generationType, thinkingEnabled, modelSupportsThinking
- **AgentState** (WARM): phase, plan, summary, toolChainSteps, currentRound
- **RagState** (WARM): context, results
- **ChatConfigState** (COLD): streamingEnabled, chatMemoryEnabled, showDynamicWindow, showModelList

Keep `messages` as `SnapshotStateList`. Keep TTS/model proxies as individual flows. Keep metrics as individual flow.

## Phase 2: File Splits

### BodyContent.kt (1,869 → 7 files)
- BodyContent.kt (200) — orchestration
- StreamingView.kt (150)
- MessageBubbles.kt (280)
- MessageActions.kt (210)
- MessageMetrics.kt (310)
- ImageGeneration.kt (190)
- RagDisplay.kt (240)

### DynamicActionWindow.kt (1,190 → 5 files)
- DynamicActionWindow.kt (150) — tab routing
- StatusStates.kt (380)
- StatusTab.kt (120)
- ModelsTab.kt (170)
- SystemTab.kt (180)

### ModelStoreScreen.kt (2,129 → 7 files)
- ModelStoreScreen.kt (100) — scaffold routing
- BrowseModelsTab.kt (280)
- InstalledModelsTab.kt (250)
- ModelCard.kt (280)
- ModelFilters.kt (330)
- RepositorySettings.kt (240)
- DeviceInfoCard.kt (100)

### SettingsScreen.kt (1,275 → 5 files)
- SettingsScreen.kt (200) — LazyColumn scaffold
- GeneralSettingsSection.kt (250)
- TtsSettingsSection.kt (180)
- DataManagementSection.kt (320)
- ModelDownloadCards.kt (150)

### HomeScreen.kt (855 → 4 files)
- HomeScreen.kt (100) — scaffold
- HomeBottomBar.kt (290)
- HomeOverlays.kt (250)
- HomeTopBar.kt (60)

## Phase 3: Design Language Standardization

All files migrate to:
- Spacing: Standards.* constants (no hardcoded dp)
- Shapes: Standards.Radius* (no hardcoded RoundedCornerShape)
- Buttons: Action* family (no raw Button/TextButton/OutlinedButton)
- Animations: Motion.* specs (no inline spring/tween)
- Containers: Surface (no Card/Box+background for interactive elements)
- Headers: `// ── Name ──` convention
- Typography: MaterialTheme.typography (no ManropeFontFamily)
- State collection: collectAsStateWithLifecycle (no collectAsState)

## Phase 4: Wire New State Pattern

Update split files to accept grouped state objects. Remove deprecated individual flow aliases.

## Constraints

- Zero behavior changes
- Zero new features
- Build must pass after each phase
- All files stay in current packages

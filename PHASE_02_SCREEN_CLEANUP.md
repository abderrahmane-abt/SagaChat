# Phase 02 — Screen, ViewModel & Navigation Cleanup

Delete every screen and viewmodel that belongs to RAG, voice, server,
plugins, image generation, and web search. Then clean `NavScreens.kt`,
`TNavigation.kt`, `AppTopBar.kt`, `AppBottomBar.kt`, and
`SettingsViewModel.kt` so the project compiles again.

All paths are relative to:
`app/src/main/java/com/dark/tool_neuron/`

---

## 1. Delete screen folders

```bash
rm -rf ui/screens/dev_notes
rm -rf ui/screens/experiment
rm -rf ui/screens/image_task
rm -rf ui/screens/plugin_hub
rm -rf ui/screens/plugin_install
rm -rf ui/screens/rag_debug
rm -rf ui/screens/server
rm -rf ui/screens/web_search
rm -rf ui/screens/guide
```

The `guide/` folder contains RP-irrelevant help pages. Delete entirely;
a new guide screen will be added in Phase 07 if wanted.

---

## 2. Delete viewmodel files

```bash
rm viewmodel/ImageTaskViewModel.kt
rm viewmodel/PluginHubViewModel.kt
rm viewmodel/PluginInstallViewModel.kt
rm viewmodel/RagDebugViewModel.kt
rm viewmodel/ServerViewModel.kt
rm viewmodel/SetupRagViewModel.kt
rm viewmodel/WebSearchCoordinator.kt
```

---

## 3. Delete model files that are only used by deleted features

```bash
rm model/WebSearchEvent.kt
rm model/WebSearchUiState.kt
rm model/ChatDocument.kt
rm model/Citation.kt
rm model/DocExtension.kt
```

---

## 4. Delete repo files that are only used by deleted features

```bash
rm repo/RagManager.kt
rm repo/RagAugmentation.kt
rm repo/RagChunker.kt
rm repo/RagCitationMatcher.kt
rm repo/RagDebugResult.kt
rm repo/RagDocSummarizer.kt
rm repo/RagKeywordIndex.kt
rm repo/RagPreferences.kt
rm repo/RagQueryRewriter.kt
rm repo/RagRaptor.kt
rm repo/RagReranker.kt
rm repo/DocumentRepository.kt
rm repo/ImageGenManager.kt
rm repo/PluginPrefsRepository.kt
rm repo/SourceFileVault.kt
rm -rf repo/web_search
```

---

## 5. Delete the voice folder

```bash
rm -rf voice/
```

---

## 6. Replace `NavScreens.kt`

Full replacement. File path:
`model/NavScreens.kt`

```kotlin
package com.dark.tool_neuron.model

import java.net.URLEncoder

sealed class NavScreens(val route: String) {
    object IntroScreen     : NavScreens("intro_screen")
    object PasswordScreen  : NavScreens("password_screen")
    object SetupScreen     : NavScreens("setup_screen")
    object SetupTheme      : NavScreens("setup_theme")
    object ModelSetup      : NavScreens("model_setup")
    object CharacterList   : NavScreens("character_list")
    object CharacterCreate : NavScreens("character_create")
    object ModelStore      : NavScreens("model_store")
    object Downloads       : NavScreens("downloads")
    object ModelManager    : NavScreens("model_manager")
    object Settings        : NavScreens("settings")
    object SettingsChatRp  : NavScreens("settings_chat_rp")
    object SettingsTheming : NavScreens("settings_theming")
    object SettingsPrivacy : NavScreens("settings_privacy")
    object SettingsDiagnostics : NavScreens("settings_diagnostics")
    object SettingsAbout   : NavScreens("settings_about")
    object SettingsPerformance : NavScreens("settings_performance")
    object SettingsModel   : NavScreens("settings_model")
    object Storage         : NavScreens("storage")
    object Credits         : NavScreens("credits")
    object HfExplorer      : NavScreens("hf_explorer")
    object HfRepoDetail    : NavScreens("hf_repo/{repoPath}") {
        const val ARG_REPO_PATH = "repoPath"
        fun routeFor(repoPath: String) = "hf_repo/${URLEncoder.encode(repoPath, "UTF-8")}"
    }
    object ModelConfig     : NavScreens("model_config/{modelId}") {
        const val ARG_MODEL_ID = "modelId"
        fun routeFor(modelId: String) = "model_config/$modelId"
    }
    object CharacterDetail : NavScreens("character_detail/{characterId}") {
        const val ARG_CHARACTER_ID = "characterId"
        fun routeFor(characterId: String) = "character_detail/$characterId"
    }
    object RoleplayChat    : NavScreens("roleplay_chat/{characterId}") {
        const val ARG_CHARACTER_ID = "characterId"
        fun routeFor(characterId: String) = "roleplay_chat/$characterId"
    }
}
```

---

## 7. Replace `TNavigation.kt`

Full replacement. File path:
`ui/navigation/TNavigation.kt`

```kotlin
package com.dark.tool_neuron.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dark.tool_neuron.model.NavScreens
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.screens.credits.CreditsScreen
import com.dark.tool_neuron.ui.screens.downloads.DownloadsScreen
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screens.intro_screen.IntroScreen
import com.dark.tool_neuron.ui.screens.model_config.ModelConfigScreen
import com.dark.tool_neuron.ui.screens.model_manager.ModelManagerScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsSectionScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsThemingScreen
import com.dark.tool_neuron.ui.screens.storage.StorageScreen
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreen
import com.dark.tool_neuron.ui.screens.model_store.ModelStoreScreen
import com.dark.tool_neuron.ui.screens.hf_explorer.HfExplorerScreen
import com.dark.tool_neuron.ui.screens.hf_explorer.HfRepoDetailScreen
import com.dark.tool_neuron.ui.screens.setup_screen.ModelSetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupPasswordScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupThemeScreen
import com.dark.tool_neuron.ui.screens.character_list.CharacterListScreen
import com.dark.tool_neuron.ui.screens.character_create.CharacterCreateScreen
import com.dark.tool_neuron.ui.screens.character_detail.CharacterDetailScreen
import com.dark.tool_neuron.ui.screens.roleplay_chat.RoleplayChatScreen
import com.dark.tool_neuron.ui.theme.rememberNavTransitions
import com.dark.tool_neuron.viewmodel.CharacterViewModel
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.PasswordViewModel
import com.dark.tool_neuron.viewmodel.RoleplayChatViewModel
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.viewmodel.ThemingViewModel
import com.dark.tool_neuron.viewmodel.SetupViewModel
import com.dark.tool_neuron.viewmodel.StorageViewModel
import com.dark.tool_neuron.model.ModelConfig
import java.net.URLDecoder

@Composable
fun TNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    startDestination: String,
    nextDestination: String,
    onUnlocked: () -> Unit = {},
    onSetupComplete: () -> Unit = {},
    onModelSetupComplete: () -> Unit = {},
) {
    val transitions = rememberNavTransitions()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        composable(
            route = NavScreens.IntroScreen.route,
            exitTransition = { fadeOut(tween(durationMillis = 800)) },
        ) {
            IntroScreen(
                innerPadding = innerPadding,
                onFinish = {
                    navController.navigate(nextDestination) {
                        popUpTo(NavScreens.IntroScreen.route) { inclusive = true }
                    }
                },
            )
        }

        composable(NavScreens.CharacterList.route) {
            val activity = LocalContext.current as ComponentActivity
            val vm: CharacterViewModel = hiltViewModel(activity)
            CharacterListScreen(
                innerPadding = innerPadding,
                viewModel = vm,
                onOpenCharacter = { id ->
                    navController.navigate(NavScreens.CharacterDetail.routeFor(id))
                },
                onCreateCharacter = { navController.navigate(NavScreens.CharacterCreate.route) },
            )
        }

        composable(NavScreens.CharacterCreate.route) {
            val activity = LocalContext.current as ComponentActivity
            val vm: CharacterViewModel = hiltViewModel(activity)
            CharacterCreateScreen(
                innerPadding = innerPadding,
                viewModel = vm,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = NavScreens.CharacterDetail.route,
            arguments = listOf(navArgument(NavScreens.CharacterDetail.ARG_CHARACTER_ID) {
                type = NavType.StringType
            }),
        ) { back ->
            val id = back.arguments?.getString(NavScreens.CharacterDetail.ARG_CHARACTER_ID) ?: return@composable
            val activity = LocalContext.current as ComponentActivity
            val vm: CharacterViewModel = hiltViewModel(activity)
            CharacterDetailScreen(
                innerPadding = innerPadding,
                characterId = id,
                viewModel = vm,
                onStartChat = { navController.navigate(NavScreens.RoleplayChat.routeFor(id)) },
                onEdit = { navController.navigate(NavScreens.CharacterCreate.route) },
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = NavScreens.RoleplayChat.route,
            arguments = listOf(navArgument(NavScreens.RoleplayChat.ARG_CHARACTER_ID) {
                type = NavType.StringType
            }),
        ) { back ->
            val id = back.arguments?.getString(NavScreens.RoleplayChat.ARG_CHARACTER_ID) ?: return@composable
            val activity = LocalContext.current as ComponentActivity
            val homeVm: HomeViewModel = hiltViewModel(activity)
            val rpVm: RoleplayChatViewModel = hiltViewModel()
            LaunchedEffect(id) { rpVm.init(id) }
            RoleplayChatScreen(
                innerPadding = innerPadding,
                characterId = id,
                homeViewModel = homeVm,
                viewModel = rpVm,
                onBack = { navController.popBackStack() },
            )
        }

        composable(NavScreens.Credits.route) {
            CreditsScreen(
                innerPadding = innerPadding,
                onExit = { navController.popBackStack() },
            )
        }

        composable(NavScreens.SetupScreen.route) {
            val viewModel: SetupViewModel = hiltViewModel()
            val selectedMode by viewModel.selectedMode.collectAsStateWithLifecycle()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val confirmPassword by viewModel.confirmPassword.collectAsStateWithLifecycle()
            val isConfirmStep by viewModel.isConfirmStep.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()

            if (selectedMode == "app_password") {
                SetupPasswordScreen(
                    innerPadding = innerPadding,
                    password = if (isConfirmStep) confirmPassword else password,
                    isConfirmStep = isConfirmStep,
                    error = error,
                    onDigit = viewModel::appendDigit,
                    onDelete = viewModel::deleteLast,
                    onClear = viewModel::clearAll,
                    onSubmit = { viewModel.submitPassword(onSuccess = onSetupComplete) },
                    onBack = viewModel::goBack,
                )
            } else {
                SetupScreen(
                    innerPadding = innerPadding,
                    selectedMode = selectedMode,
                    onModeSelected = { mode ->
                        viewModel.selectMode(mode)
                        if (mode == "none") {
                            viewModel.completeWithNoLock()
                            onSetupComplete()
                        }
                    },
                )
            }
        }

        composable(NavScreens.SetupTheme.route) {
            SetupThemeScreen(innerPadding = innerPadding)
        }

        composable(NavScreens.ModelStore.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelStoreScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateToHfExplorer = { navController.navigate(NavScreens.HfExplorer.route) },
                onNavigateToDownloads = { navController.navigate(NavScreens.Downloads.route) },
            )
        }

        composable(NavScreens.Downloads.route) {
            DownloadsScreen(innerPadding = innerPadding)
        }

        composable(NavScreens.ModelSetup.route) {
            val activity = LocalContext.current as ComponentActivity
            val storeVm: ModelStoreViewModel = hiltViewModel(activity)
            ModelSetupScreen(
                innerPadding = innerPadding,
                onPackSelected = { packId ->
                    storeVm.downloadPack(packId)
                    onModelSetupComplete()
                },
                onOpenStore = { navController.navigate(NavScreens.ModelStore.route) },
                onLocalImport = { uri, name, size, type ->
                    storeVm.importLocalModel(uri, name, size, type)
                    onModelSetupComplete()
                },
                onSkip = { onModelSetupComplete() },
            )
        }

        composable(NavScreens.Settings.route) {
            SettingsScreen(
                innerPadding = innerPadding,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.SettingsChatRp.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_CHAT_RP,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.SettingsTheming.route) {
            val viewModel: ThemingViewModel = hiltViewModel()
            SettingsThemingScreen(innerPadding = innerPadding, viewModel = viewModel)
        }

        composable(NavScreens.SettingsPrivacy.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PRIVACY,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.SettingsDiagnostics.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_DIAGNOSTICS,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.SettingsAbout.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_ABOUT,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.SettingsPerformance.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_PERFORMANCE,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.SettingsModel.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsSectionScreen(
                innerPadding = innerPadding,
                sectionId = SettingsViewModel.SECTION_MODEL,
                viewModel = viewModel,
                onNavigate = { route -> navController.navigate(route) },
            )
        }

        composable(NavScreens.Storage.route) {
            val viewModel: StorageViewModel = hiltViewModel()
            StorageScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateToModelManager = { navController.navigate(NavScreens.ModelManager.route) },
                onNavigateToStore = { navController.navigate(NavScreens.ModelStore.route) },
            )
        }

        composable(NavScreens.ModelManager.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelManagerScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onEditModel = { modelId -> navController.navigate(NavScreens.ModelConfig.routeFor(modelId)) },
            )
        }

        composable(
            route = NavScreens.ModelConfig.route,
            arguments = listOf(navArgument(NavScreens.ModelConfig.ARG_MODEL_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            val installed by viewModel.installedModels.collectAsStateWithLifecycle()
            val modelId = backStackEntry.arguments?.getString(NavScreens.ModelConfig.ARG_MODEL_ID)
            val model = installed.firstOrNull { it.id == modelId }
            if (model == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                var initialConfig by androidx.compose.runtime.remember(model.id) {
                    androidx.compose.runtime.mutableStateOf<ModelConfig?>(null)
                }
                var loaded by androidx.compose.runtime.remember(model.id) {
                    androidx.compose.runtime.mutableStateOf(false)
                }
                LaunchedEffect(model.id) {
                    initialConfig = viewModel.getModelConfig(model.id)
                    loaded = true
                }
                if (loaded) {
                    ModelConfigScreen(
                        modelInfo = model,
                        initialConfig = initialConfig,
                        onSave = { config ->
                            viewModel.saveModelConfig(config)
                            navController.popBackStack()
                        },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }

        composable(NavScreens.HfExplorer.route) {
            HfExplorerScreen(innerPadding = innerPadding, navController = navController)
        }

        composable(NavScreens.HfRepoDetail.route) { backStack ->
            val raw = backStack.arguments?.getString(NavScreens.HfRepoDetail.ARG_REPO_PATH).orEmpty()
            val decoded = URLDecoder.decode(raw, "UTF-8")
            HfRepoDetailScreen(innerPadding = innerPadding, repoPath = decoded)
        }

        composable(NavScreens.PasswordScreen.route) {
            val viewModel: PasswordViewModel = hiltViewModel()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
            val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()
            val lockedUntilMs by viewModel.lockedUntilMs.collectAsStateWithLifecycle()
            val wiped by viewModel.wiped.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { viewModel.reset() }
            LaunchedEffect(unlocked) { if (unlocked) onUnlocked() }

            PasswordScreen(
                innerPadding = innerPadding,
                password = password,
                error = error,
                isVerifying = isVerifying,
                lockedUntilMs = lockedUntilMs,
                wiped = wiped,
                onDigit = viewModel::appendDigit,
                onDelete = viewModel::deleteLast,
                onClear = viewModel::clearAll,
                onSubmit = viewModel::submit,
            )
        }
    }
}
```

---

## 8. Clean `SettingsViewModel.kt`

Open `viewmodel/SettingsViewModel.kt`. Make these changes:

**Remove constants:**
```kotlin
const val SECTION_CHAT_RAG = "chat_rag"     // remove
const val SECTION_VOICE = "voice"            // remove
const val SECTION_VISION = "vision"          // remove
const val SECTION_PLUGINS = "plugins"        // remove
```

**Add constant:**
```kotlin
const val SECTION_CHAT_RP = "chat_rp"
```

**Remove all sections** whose `sectionId` matches any of:
- `SECTION_CHAT_RAG`
- `SECTION_VOICE`
- `SECTION_VISION`
- `SECTION_PLUGINS`

Remove all injected dependencies that only exist for those sections:
- `RagManager`
- `RagPreferences`
- `VoiceModelManager`
- `PluginPrefsRepository`
- `ServerController`

**Add new section** `SECTION_CHAT_RP` with settings:
- User display name (string key `user_display_name`, default `"You"`)
- Response length style (enum: `short` / `medium` / `long`, default `medium`)
- Memory mode enabled (bool key `memory_enabled`, default `true`)

---

## 9. Clean `HomeViewModel.kt`

Open `viewmodel/HomeViewModel.kt`. Remove all fields and logic related to:
- Voice (`VoiceModelManager`, `speakingMessageId`, `isRecording`, etc.)
- RAG (`RagManager`, `chatDocuments`, `ragReady`, etc.)
- Web search (`WebSearchCoordinator`, `_webSearchEnabled`, `webSearchMessages`, etc.)
- VLM (`VlmVisionCacheRepository`, `vlmCache`, `isVlmLoaded`, `pendingImages`, etc.)
- Server (`ServerController`)
- Tool calls that reference deleted imports

Remove injected parameters that no longer exist:
```kotlin
private val ragManager: RagManager,
private val voiceManager: VoiceModelManager,
private val serverController: ServerController,
private val webSearchCoordinator: WebSearchCoordinator,
private val vlmCache: VlmVisionCacheRepository,
```

Keep the core inference loop (`sendMessage`, `runGeneration`, `stopGeneration`,
`selectChat`, `createNewChat`, `deleteChat`, `messages`, `isGenerating`,
`streamingFragment`, `modelLoadState`, `chats`, `activeModel`,
`installedModels`, `generationStatus`, `pillState`).

Also keep `compactConversation()` — it will power the memory summarization
step in Phase 04.

---

## 10. Clean `AppPreferences.kt`

Open `data/AppPreferences.kt`. Remove keys that reference deleted features:
- Any key for RAG settings
- Any key for voice settings
- Any key for vision/VLM settings
- Any key for plugin settings
- Any key for server settings

Add new keys:
```kotlin
var userDisplayName: String
    get() = getString("user_display_name", "You")
    set(v) = putString("user_display_name", v)

var responseLengthStyle: String
    get() = getString("rp_response_length", "medium")
    set(v) = putString("rp_response_length", v)

var memoryEnabled: Boolean
    get() = getBool("memory_enabled", true)
    set(v) = putBool("memory_enabled", v)
```

---

## 11. Verification

```bash
./gradlew :app:compileDebugKotlin
```

Expected: all red imports from deleted files are gone. The only unresolved
symbols should be the 4 new screen imports added to `TNavigation.kt`
(`CharacterListScreen`, `CharacterCreateScreen`, `CharacterDetailScreen`,
`RoleplayChatScreen`) and the 2 new VMs (`CharacterViewModel`,
`RoleplayChatViewModel`). Those will be created in Phases 03–06.

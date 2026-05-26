package com.moorixlabs.sagachat.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.moorixlabs.sagachat.model.ModelConfig
import com.moorixlabs.sagachat.model.NavScreens
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moorixlabs.sagachat.ui.screens.credits.CreditsScreen
import com.moorixlabs.sagachat.ui.screens.downloads.DownloadsScreen
import com.moorixlabs.sagachat.ui.screens.intro_screen.IntroScreen
import com.moorixlabs.sagachat.ui.screens.model_config.ModelConfigScreen
import com.moorixlabs.sagachat.ui.screens.model_manager.ModelManagerScreen
import com.moorixlabs.sagachat.ui.screens.settings.SettingsScreen
import com.moorixlabs.sagachat.ui.screens.settings.SettingsSectionScreen
import com.moorixlabs.sagachat.ui.screens.settings.SettingsThemingScreen
import com.moorixlabs.sagachat.ui.screens.settings.SettingsChatRpScreen
import com.moorixlabs.sagachat.ui.screens.storage.StorageScreen
import com.moorixlabs.sagachat.ui.screens.password_screen.PasswordScreen
import com.moorixlabs.sagachat.ui.screens.model_store.ModelStoreScreen
import com.moorixlabs.sagachat.ui.screens.hf_explorer.HfExplorerScreen
import com.moorixlabs.sagachat.ui.screens.hf_explorer.HfRepoDetailScreen
import com.moorixlabs.sagachat.ui.screens.setup_screen.ModelSetupScreen
import com.moorixlabs.sagachat.ui.screens.setup_screen.SetupPasswordScreen
import com.moorixlabs.sagachat.ui.screens.setup_screen.SetupScreen
import com.moorixlabs.sagachat.ui.screens.setup_screen.SetupThemeScreen
import com.moorixlabs.sagachat.ui.screens.terms_conditions.TermsConditionsScreen
import com.moorixlabs.sagachat.ui.theme.rememberNavTransitions
import com.moorixlabs.sagachat.viewmodel.ModelStoreViewModel
import com.moorixlabs.sagachat.viewmodel.PasswordViewModel
import com.moorixlabs.sagachat.viewmodel.SettingsViewModel
import com.moorixlabs.sagachat.viewmodel.ThemingViewModel
import com.moorixlabs.sagachat.viewmodel.SetupViewModel
import com.moorixlabs.sagachat.viewmodel.StorageViewModel
import com.moorixlabs.sagachat.ui.screens.character_list.CharacterListScreen
import com.moorixlabs.sagachat.ui.screens.character_detail.CharacterDetailScreen
import com.moorixlabs.sagachat.ui.screens.character_create.CharacterCreateScreen
import com.moorixlabs.sagachat.ui.screens.roleplay_chat.RoleplayChatScreen
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

        composable(NavScreens.TermsConditions.route) {
            TermsConditionsScreen(
                innerPadding = innerPadding,
                onAccept = {},
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
            SettingsChatRpScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
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
                onEditModel = { modelId ->
                    navController.navigate(NavScreens.ModelConfig.routeFor(modelId))
                },
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
                var initialConfig by remember(model.id) { mutableStateOf<ModelConfig?>(null) }
                var loaded by remember(model.id) { mutableStateOf(false) }
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

        // ── Roleplay routes ───────────────────────────────────────────────

        composable(NavScreens.CharacterList.route) {
            CharacterListScreen(
                innerPadding = innerPadding,
                onOpenCharacter = { id -> navController.navigate(NavScreens.CharacterDetail.routeFor(id)) },
                onCreateCharacter = { navController.navigate(NavScreens.CharacterCreate.route) },
            )
        }

        composable(NavScreens.CharacterCreate.route) {
            CharacterCreateScreen(
                editCharacterId = null,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(NavScreens.CharacterDetail.routeFor(id)) {
                        popUpTo(NavScreens.CharacterCreate.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = NavScreens.CharacterEdit.route,
            arguments = listOf(navArgument(NavScreens.CharacterEdit.ARG_CHARACTER_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString(NavScreens.CharacterEdit.ARG_CHARACTER_ID) ?: ""
            CharacterCreateScreen(
                editCharacterId = characterId,
                onBack = { navController.popBackStack() },
                onSaved = { id ->
                    navController.navigate(NavScreens.CharacterDetail.routeFor(id)) {
                        popUpTo(NavScreens.CharacterEdit.route) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = NavScreens.CharacterDetail.route,
            arguments = listOf(navArgument(NavScreens.CharacterDetail.ARG_CHARACTER_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString(NavScreens.CharacterDetail.ARG_CHARACTER_ID) ?: ""
            CharacterDetailScreen(
                characterId = characterId,
                onBack = { navController.popBackStack() },
                onStartChat = { id -> navController.navigate(NavScreens.RoleplayChat.routeFor(id)) },
                onEdit = { id -> navController.navigate(NavScreens.CharacterEdit.routeFor(id)) },
            )
        }

        composable(
            route = NavScreens.RoleplayChat.route,
            arguments = listOf(navArgument(NavScreens.RoleplayChat.ARG_CHARACTER_ID) {
                type = NavType.StringType
            }),
        ) { backStackEntry ->
            val characterId = backStackEntry.arguments?.getString(NavScreens.RoleplayChat.ARG_CHARACTER_ID) ?: ""
            RoleplayChatScreen(
                characterId = characterId,
                innerPadding = innerPadding,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

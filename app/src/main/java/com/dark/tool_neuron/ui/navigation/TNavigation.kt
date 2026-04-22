package com.dark.tool_neuron.ui.navigation

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.dark.tool_neuron.model.NavScreens
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesScreen
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreen
import com.dark.tool_neuron.ui.screens.intro_screen.IntroScreen
import com.dark.tool_neuron.ui.screens.model_config.ModelConfigScreen
import com.dark.tool_neuron.ui.screens.model_manager.ModelManagerScreen
import com.dark.tool_neuron.ui.screens.settings.SettingsScreen
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreen
import com.dark.tool_neuron.ui.screens.guide.AppGuideScreen
import com.dark.tool_neuron.ui.screens.model_store.ModelStoreScreen
import com.dark.tool_neuron.ui.screens.plugin_hub.PluginHubScreen
import com.dark.tool_neuron.ui.screens.setup_screen.ModelSetupScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupPasswordScreen
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreen
import com.dark.tool_neuron.ui.theme.rememberNavTransitions
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ModelStoreViewModel
import com.dark.tool_neuron.viewmodel.PasswordViewModel
import com.dark.tool_neuron.viewmodel.SettingsViewModel
import com.dark.tool_neuron.viewmodel.SetupViewModel
import kotlinx.coroutines.delay

@Composable
fun TNavigation(
    navController: NavHostController,
    innerPadding: PaddingValues,
    startDestination: String,
    nextDestination: String,
    actionWindowExpanded: Boolean,
    onActionWindowDismiss: () -> Unit,
    onUnlocked: () -> Unit = {},
    onSetupComplete: () -> Unit = {},
    onModelSetupComplete: () -> Unit = {},
) {
    val transitions = rememberNavTransitions()

    LaunchedEffect(Unit) {
        delay(2000)
        navController.navigate(nextDestination) {
            popUpTo(NavScreens.IntroScreen.route) { inclusive = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = transitions.enter,
        exitTransition = transitions.exit,
        popEnterTransition = transitions.popEnter,
        popExitTransition = transitions.popExit,
    ) {
        composable(NavScreens.IntroScreen.route) {
            IntroScreen(innerPadding)
        }
        composable(NavScreens.HomeScreen.route) {
            val activity = LocalContext.current as ComponentActivity
            val homeViewModel: HomeViewModel = hiltViewModel(activity)
            HomeScreen(
                innerPadding = innerPadding,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowDismiss = onActionWindowDismiss,
                onOpenModelManager = { navController.navigate(NavScreens.ModelManager.route) },
                viewModel = homeViewModel,
            )
        }
        composable(NavScreens.DevNotes.route) {
            DevNotesScreen(innerPadding = innerPadding)
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
                    onSubmit = {
                        if (viewModel.submitPassword()) onSetupComplete()
                    },
                    onBack = viewModel::goBack
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
                    }
                )
            }
        }
        composable(NavScreens.ModelStore.route) {
            val activity = LocalContext.current as ComponentActivity
            val viewModel: ModelStoreViewModel = hiltViewModel(activity)
            ModelStoreScreen(
                innerPadding = innerPadding,
                viewModel = viewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(NavScreens.ModelSetup.route) {
            val activity = LocalContext.current as ComponentActivity
            val storeVm: ModelStoreViewModel = hiltViewModel(activity)
            val catalogModels by storeVm.filteredModels.collectAsStateWithLifecycle()

            ModelSetupScreen(
                innerPadding = innerPadding,
                onModelSelected = { modelId ->
                    val model = catalogModels.firstOrNull { it.id.startsWith(modelId) }
                        ?: catalogModels.firstOrNull { it.repoId == modelId }
                    if (model != null) storeVm.downloadModel(model)
                    onModelSetupComplete()
                },
                onOpenStore = { navController.navigate(NavScreens.ModelStore.route) },
                onImportLocal = { },
                onSkip = { onModelSetupComplete() }
            )
        }
        composable(NavScreens.AppGuide.route) {
            AppGuideScreen(
                onClose = { navController.popBackStack() }
            )
        }
        composable(NavScreens.PluginHub.route) {
            PluginHubScreen(
                onClose = { navController.popBackStack() }
            )
        }
        composable(NavScreens.Settings.route) {
            val viewModel: SettingsViewModel = hiltViewModel()
            SettingsScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
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
                var initialConfig by remember(model.id) {
                    mutableStateOf<com.dark.tool_neuron.model.ModelConfig?>(null)
                }
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
        composable(NavScreens.PasswordScreen.route) {
            val viewModel: PasswordViewModel = hiltViewModel()
            val password by viewModel.password.collectAsStateWithLifecycle()
            val error by viewModel.error.collectAsStateWithLifecycle()
            val isVerifying by viewModel.isVerifying.collectAsStateWithLifecycle()
            val unlocked by viewModel.unlocked.collectAsStateWithLifecycle()

            LaunchedEffect(Unit) { viewModel.reset() }
            LaunchedEffect(unlocked) {
                if (unlocked) onUnlocked()
            }

            PasswordScreen(
                innerPadding = innerPadding,
                password = password,
                error = error,
                isVerifying = isVerifying,
                onDigit = viewModel::appendDigit,
                onDelete = viewModel::deleteLast,
                onClear = viewModel::clearAll,
                onSubmit = viewModel::submit
            )
        }
    }
}

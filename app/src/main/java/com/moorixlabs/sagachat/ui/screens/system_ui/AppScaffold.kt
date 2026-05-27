package com.moorixlabs.sagachat.ui.screens.system_ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import com.moorixlabs.sagachat.ui.util.ProvideWindowMetrics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.ui.components.RootWarningDialog
import com.moorixlabs.sagachat.ui.navigation.TNavigation
import com.moorixlabs.sagachat.viewmodel.HomeViewModel
import com.moorixlabs.sagachat.viewmodel.ScaffoldViewModel
import kotlinx.coroutines.launch

@Composable
fun AppScaffold() {
    ProvideWindowMetrics {
        AppScaffoldInner()
    }
}

@Composable
private fun AppScaffoldInner() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val scaffoldViewModel: ScaffoldViewModel = hiltViewModel()
    val pillState by homeViewModel.pillState.collectAsStateWithLifecycle()
    val downloadProgress by scaffoldViewModel.downloadProgress.collectAsStateWithLifecycle()

    val nextDestination = remember { scaffoldViewModel.resolveStartDestination() }
    val shouldLock by scaffoldViewModel.shouldLock.collectAsStateWithLifecycle()
    val rootWarning by scaffoldViewModel.rootWarning.collectAsStateWithLifecycle()

    rootWarning?.let { warning ->
        RootWarningDialog(
            rootEvidence = warning.rootEvidence,
            tamperEvidence = warning.tamperEvidence,
            a11yPackages = warning.a11yPackages,
            onAcknowledge = scaffoldViewModel::acknowledgeRootWarning,
        )
    }

    LaunchedEffect(shouldLock, currentRoute) {
        if (shouldLock && currentRoute != null &&
            currentRoute != NavScreens.PasswordScreen.route &&
            currentRoute != NavScreens.SetupScreen.route &&
            currentRoute != NavScreens.IntroScreen.route
        ) {
            navController.navigate(NavScreens.PasswordScreen.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route
            || currentRoute == NavScreens.PasswordScreen.route
            || currentRoute == NavScreens.Credits.route

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = !isFullscreen,
        drawerContent = {
            AppSideMenu(
                currentRoute = currentRoute,
                navController = navController,
                onCloseDrawer = { scope.launch { drawerState.close() } }
            )
        },
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                if (!isFullscreen) AppTopBar(
                    currentRoute = currentRoute,
                    pillState = pillState,
                    actionWindowExpanded = false,
                    downloadProgress = downloadProgress,
                    onActionWindowToggle = {},
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateToStore = { navController.navigate(NavScreens.ModelStore.route) },
                )
            },
        ) { innerPadding ->
            TNavigation(
                navController = navController,
                innerPadding = innerPadding,
                startDestination = NavScreens.IntroScreen.route,
                nextDestination = nextDestination,
                onUnlocked = {
                    navController.navigate(NavScreens.CharacterList.route) {
                        popUpTo(NavScreens.PasswordScreen.route) { inclusive = true }
                    }
                },
                onSetupComplete = {
                    navController.navigate(NavScreens.SetupTheme.route) {
                        popUpTo(NavScreens.SetupScreen.route) { inclusive = true }
                    }
                },
                onModelSetupComplete = {
                    scaffoldViewModel.markModelSetupDone()
                    navController.navigate(NavScreens.CharacterList.route) {
                        popUpTo(NavScreens.ModelSetup.route) { inclusive = true }
                    }
                },
                onMenuClick = {
                    scope.launch { drawerState.open() }
                },
            )
        }
    }
}

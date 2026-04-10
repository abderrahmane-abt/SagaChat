package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.navigation.TNavigation
import com.dark.tool_neuron.viewmodel.HomeViewModel
import com.dark.tool_neuron.viewmodel.ScaffoldViewModel

@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val currentEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentEntry?.destination?.route

    val homeViewModel: HomeViewModel = hiltViewModel()
    val scaffoldViewModel: ScaffoldViewModel = hiltViewModel()
    val actionWindowExpanded by homeViewModel.actionWindowExpanded.collectAsStateWithLifecycle()

    val nextDestination = remember { scaffoldViewModel.resolveStartDestination() }

    val isFullscreen = currentRoute == NavScreens.IntroScreen.route
            || currentRoute == NavScreens.PasswordScreen.route

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (!isFullscreen) AppTopBar(
                currentRoute = currentRoute,
                actionWindowExpanded = actionWindowExpanded,
                onActionWindowToggle = homeViewModel::toggleActionWindow
            )
        },
        bottomBar = {
            if (!isFullscreen) AppBottomBar(
                currentRoute = currentRoute,
                navController = navController,
                onOnboardingComplete = { scaffoldViewModel.markOnboardingComplete() }
            )
        },
    ) { innerPadding ->
        TNavigation(
            navController = navController,
            innerPadding = innerPadding,
            startDestination = NavScreens.IntroScreen.route,
            nextDestination = nextDestination,
            actionWindowExpanded = actionWindowExpanded,
            onActionWindowDismiss = homeViewModel::collapseActionWindow,
            onUnlocked = {
                navController.navigate(NavScreens.HomeScreen.route) {
                    popUpTo(NavScreens.PasswordScreen.route) { inclusive = true }
                }
            },
            onSetupComplete = {
                navController.navigate(NavScreens.HomeScreen.route) {
                    popUpTo(NavScreens.SetupScreen.route) { inclusive = true }
                }
            }
        )
    }
}

package com.dark.tool_neuron.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.HomeScreen
import com.dark.tool_neuron.ui.screens.IntroScreen
import com.dark.tool_neuron.ui.theme.rememberNavTransitions

@Composable
fun TNavigation(
    innerPadding: PaddingValues,
    startDestination: String = ""
) {
    val navController = rememberNavController()
    val transitions = rememberNavTransitions()

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
            HomeScreen(innerPadding)
        }
    }
}

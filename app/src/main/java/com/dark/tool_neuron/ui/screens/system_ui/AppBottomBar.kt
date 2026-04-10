package com.dark.tool_neuron.ui.screens.system_ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import com.dark.tool_neuron.model.NavScreens
import com.dark.tool_neuron.ui.screens.dev_notes.DevNotesBottomBar
import com.dark.tool_neuron.ui.screens.home_screen.HomeScreenBottomBar
import com.dark.tool_neuron.ui.screens.password_screen.PasswordScreenBottomBar
import com.dark.tool_neuron.ui.screens.setup_screen.SetupScreenBottomBar

@Composable
fun AppBottomBar(
    currentRoute: String?,
    navController: NavHostController,
    onOnboardingComplete: () -> Unit = {}
) {
    when (currentRoute) {
        NavScreens.HomeScreen.route -> HomeScreenBottomBar(navController)
        NavScreens.DevNotes.route -> DevNotesBottomBar(navController, onContinue = onOnboardingComplete)
        NavScreens.PasswordScreen.route -> PasswordScreenBottomBar()
        else -> Unit
    }
}

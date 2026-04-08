package com.dark.tool_neuron.model

sealed class NavScreens(val route: String) {
    object IntroScreen : NavScreens("intro_screen")
    object HomeScreen : NavScreens("home_screen")
    object DevNotes : NavScreens("dev_notes")
}
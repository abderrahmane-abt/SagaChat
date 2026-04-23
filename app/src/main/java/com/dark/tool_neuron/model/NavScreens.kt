package com.dark.tool_neuron.model

sealed class NavScreens(val route: String) {
    object IntroScreen : NavScreens("intro_screen")
    object HomeScreen : NavScreens("home_screen")
    object DevNotes : NavScreens("dev_notes")
    object PasswordScreen : NavScreens("password_screen")
    object SetupScreen : NavScreens("setup_screen")
    object SetupTheme : NavScreens("setup_theme")
    object ModelStore : NavScreens("model_store")
    object ModelSetup : NavScreens("model_setup")
    object AppGuide : NavScreens("app_guide")
    object GuideChat : NavScreens("guide_chat")
    object GuideModels : NavScreens("guide_models")
    object GuideRag : NavScreens("guide_rag")
    object GuideVlm : NavScreens("guide_vlm")
    object GuideVoice : NavScreens("guide_voice")
    object GuideSecurity : NavScreens("guide_security")
    object GuideThemes : NavScreens("guide_themes")
    object PluginHub : NavScreens("plugin_hub")
    object ModelManager : NavScreens("model_manager")
    object Settings : NavScreens("settings")
    object ModelConfig : NavScreens("model_config/{modelId}") {
        const val ARG_MODEL_ID = "modelId"
        fun routeFor(modelId: String) = "model_config/$modelId"
    }
}
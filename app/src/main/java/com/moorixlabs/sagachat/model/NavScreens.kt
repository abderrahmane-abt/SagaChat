package com.moorixlabs.sagachat.model

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
    object TermsConditions : NavScreens("terms_conditions")
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
    object CharacterEdit   : NavScreens("character_edit/{characterId}") {
        const val ARG_CHARACTER_ID = "characterId"
        fun routeFor(characterId: String) = "character_edit/$characterId"
    }
}
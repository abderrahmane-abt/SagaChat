package com.moorixlabs.sagachat.ui.screens.system_ui

import androidx.compose.runtime.Composable
import com.moorixlabs.sagachat.model.DownloadProgress
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.ui.screens.downloads.DownloadsTopBar
import com.moorixlabs.sagachat.ui.screens.home_screen.HomeScreenTopbar
import com.moorixlabs.sagachat.ui.screens.password_screen.PasswordScreenTopBar
import com.moorixlabs.sagachat.ui.screens.settings.SettingsTopBar
import com.moorixlabs.sagachat.ui.screens.setup_screen.SetupScreenTopBar
import com.moorixlabs.sagachat.ui.screens.storage.StorageTopBar
import com.moorixlabs.sagachat.ui.screens.terms_conditions.TermsConditionsTopBar
import com.moorixlabs.sagachat.viewmodel.home_vm.PillState

@Composable
fun AppTopBar(
    currentRoute: String?,
    pillState: PillState,
    actionWindowExpanded: Boolean,
    downloadProgress: DownloadProgress? = null,
    onActionWindowToggle: () -> Unit,
    onMenuClick: () -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToStore: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.TermsConditions.route -> TermsConditionsTopBar()
        NavScreens.PasswordScreen.route -> PasswordScreenTopBar()
        NavScreens.SetupScreen.route -> SetupScreenTopBar()
        NavScreens.ModelSetup.route -> SetupScreenTopBar()
        NavScreens.ModelStore.route -> Unit
        NavScreens.Downloads.route -> DownloadsTopBar(onBack = onBack)
        NavScreens.SetupTheme.route -> SetupScreenTopBar()
        NavScreens.ModelManager.route -> Unit
        NavScreens.Settings.route -> SettingsTopBar(
            onBack = onBack,
            isMenu = true,
            onMenuClick = onMenuClick,
        )
        NavScreens.SettingsChatRp.route -> SettingsTopBar(
            onBack = onBack,
            title = "Chat & Roleplay",
            subtitle = "Character interaction and conversation style",
        )
        NavScreens.SettingsTheming.route -> SettingsTopBar(
            onBack = onBack,
            title = "Theming",
            subtitle = "Mode and color palette",
        )
        NavScreens.SettingsPrivacy.route -> SettingsTopBar(
            onBack = onBack,
            title = "Privacy",
            subtitle = "App lock and panic PIN",
        )
        NavScreens.SettingsPerformance.route -> SettingsTopBar(
            onBack = onBack,
            title = "Performance",
            subtitle = "Thread placement and decode priority",
        )
        NavScreens.SettingsModel.route -> SettingsTopBar(
            onBack = onBack,
            title = "Model",
            subtitle = "Performance and per-model configuration",
        )
        NavScreens.SettingsDiagnostics.route -> SettingsTopBar(
            onBack = onBack,
            title = "Diagnostics",
            subtitle = "Errors, crashes, and exportable bundles",
        )
        NavScreens.SettingsAbout.route -> SettingsTopBar(
            onBack = onBack,
            title = "About",
            subtitle = "App info and legal",
        )
        NavScreens.Storage.route -> StorageTopBar(onBack = onBack)
        NavScreens.HfExplorer.route -> SettingsTopBar(title = "HF Explorer", onBack = onBack)
        else -> {
            if (currentRoute?.startsWith("hf_repo/") == true) {
                SettingsTopBar(title = "Repository", onBack = onBack)
            } else Unit
        }
    }
}

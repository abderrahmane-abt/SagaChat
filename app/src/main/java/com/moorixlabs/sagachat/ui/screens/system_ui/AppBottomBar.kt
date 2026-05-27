package com.moorixlabs.sagachat.ui.screens.system_ui

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavHostController
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.screens.password_screen.PasswordScreenBottomBar
import com.moorixlabs.sagachat.ui.screens.setup_screen.SetupThemeBottomBar
import com.moorixlabs.sagachat.ui.screens.terms_conditions.TermsConditionsBottomBar

data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val rpBottomTabs = listOf(
    BottomTab(
        route = NavScreens.CharacterList.route,
        label = "Characters",
        icon = TnIcons.HatGlasses,
    ),
    BottomTab(
        route = NavScreens.ModelStore.route,
        label = "Models",
        icon = TnIcons.Cpu,
    ),
    BottomTab(
        route = NavScreens.Settings.route,
        label = "Settings",
        icon = TnIcons.Settings,
    ),
)

@Composable
fun AppBottomBar(
    currentRoute: String?,
    navController: NavHostController,
    onThemeSetupComplete: () -> Unit = {},
    onTermsAccepted: () -> Unit = {},
) {
    when (currentRoute) {
        NavScreens.TermsConditions.route -> TermsConditionsBottomBar(
            buttonLabel = "Agree and continue",
            onAccept = onTermsAccepted,
        )
        NavScreens.PasswordScreen.route -> PasswordScreenBottomBar()
        NavScreens.SetupTheme.route -> SetupThemeBottomBar(onContinue = onThemeSetupComplete)
        NavScreens.ModelSetup.route -> Unit
        else -> {
            val activeTab = getActiveTab(currentRoute)
            if (activeTab != null) {
                MainBottomBar(
                    activeRoute = activeTab,
                    onTabSelected = { route ->
                        if (currentRoute != route) {
                            navController.navigate(route) {
                                popUpTo(NavScreens.CharacterList.route) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun MainBottomBar(
    activeRoute: String,
    onTabSelected: (String) -> Unit,
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        rpBottomTabs.forEach { tab ->
            val isSelected = tab.route == activeRoute
            NavigationBarItem(
                selected = isSelected,
                onClick = { onTabSelected(tab.route) },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                    )
                },
                label = {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelMedium,
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                )
            )
        }
    }
}

private fun getActiveTab(currentRoute: String?): String? {
    if (currentRoute == null) return null
    return when {
        currentRoute.startsWith("character_list") -> NavScreens.CharacterList.route
        currentRoute.startsWith("model_store") ||
        currentRoute.startsWith("downloads") ||
        currentRoute.startsWith("model_manager") ||
        currentRoute.startsWith("model_config") ||
        currentRoute.startsWith("hf_explorer") ||
        currentRoute.startsWith("hf_repo") -> NavScreens.ModelStore.route
        currentRoute.startsWith("storage") ||
        currentRoute.startsWith("settings") -> NavScreens.Settings.route
        else -> null
    }
}

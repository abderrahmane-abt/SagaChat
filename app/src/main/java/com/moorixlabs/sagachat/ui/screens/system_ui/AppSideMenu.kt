package com.moorixlabs.sagachat.ui.screens.system_ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.ui.icons.TnIcons

data class SideTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val rpSideTabs = listOf(
    SideTab(
        route = NavScreens.CharacterList.route,
        label = "Characters",
        icon = TnIcons.HatGlasses,
    ),
    SideTab(
        route = NavScreens.ModelStore.route,
        label = "Models",
        icon = TnIcons.Cpu,
    ),
    SideTab(
        route = NavScreens.Settings.route,
        label = "Settings",
        icon = TnIcons.Settings,
    ),
)

@Composable
fun AppSideMenu(
    currentRoute: String?,
    navController: NavHostController,
) {
    val activeTab = getActiveTab(currentRoute) ?: return

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        header = {
            Box(
                modifier = Modifier
                    .padding(vertical = 16.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TnIcons.Sparkles,
                    contentDescription = "SagaChat",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        modifier = Modifier.fillMaxHeight(),
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        rpSideTabs.forEach { tab ->
            val isSelected = tab.route == activeTab
            NavigationRailItem(
                selected = isSelected,
                onClick = {
                    if (currentRoute != tab.route) {
                        navController.navigate(tab.route) {
                            popUpTo(NavScreens.CharacterList.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
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
                colors = NavigationRailItemDefaults.colors(
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

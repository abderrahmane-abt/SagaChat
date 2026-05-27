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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.ui.icons.TnIcons

data class SideTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

val mainSideTabs = listOf(
    SideTab(
        route = NavScreens.CharacterList.route,
        label = "Home",
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
    onCloseDrawer: () -> Unit,
) {
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surface,
    ) {
        Spacer(Modifier.height(12.dp))
        
        // Header
        Row(
            modifier = Modifier
                .padding(horizontal = 28.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = TnIcons.Sparkles,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "SagaChat",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 28.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
        
        Spacer(Modifier.height(16.dp))

        val activeTab = getActiveTab(currentRoute)

        mainSideTabs.forEach { tab ->
            val isSelected = tab.route == activeTab
            NavigationDrawerItem(
                label = { 
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    ) 
                },
                selected = isSelected,
                onClick = {
                    onCloseDrawer()
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
                        contentDescription = null,
                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                colors = NavigationDrawerItemDefaults.colors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
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

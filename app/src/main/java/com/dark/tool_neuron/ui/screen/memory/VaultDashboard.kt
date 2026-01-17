package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewmodel.memory.VaultInspectorScreen
import kotlinx.coroutines.launch

enum class VaultScreen(
    val title: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
    val description: String
) {
    DATA_EXPLORER(
        "Data Explorer",
        Icons.Outlined.Layers,
        Icons.Filled.Layers,
        "Browse all vault data"
    ),
    MANAGEMENT(
        "Management",
        Icons.Outlined.Dashboard,
        Icons.Filled.Dashboard,
        "Stats & operations"
    ),
    LOGGER(
        "Logger",
        Icons.Outlined.Terminal,
        Icons.Filled.Terminal,
        "View operation logs"
    ),
    INSPECTOR(
        "Inspector",
        Icons.Outlined.BugReport,
        Icons.Filled.BugReport,
        "Debug & inspect"
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboard() {
    var selectedScreen by remember { mutableStateOf(VaultScreen.DATA_EXPLORER) }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(
                    topEnd = rDp(20.dp),
                    bottomEnd = rDp(20.dp)
                ),
                drawerContainerColor = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(rDp(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = rDp(16.dp)),
                        horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(rDp(44.dp))
                                .clip(RoundedCornerShape(rDp(12.dp)))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.Memory,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(24.dp)),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Column {
                            Text(
                                "Memory Vault",
                                fontSize = rSp(18.sp),
                                fontWeight = FontWeight.Bold,
                                fontFamily = ManropeFontFamily
                            )
                            Text(
                                "Data Management",
                                fontSize = rSp(11.sp),
                                fontFamily = maple,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = rDp(8.dp)))

                    // Navigation items
                    VaultScreen.entries.forEach { screen ->
                        VaultNavItem(
                            screen = screen,
                            isSelected = selectedScreen == screen,
                            onClick = { selectedScreen = screen }
                        )
                    }

                    Spacer(Modifier.weight(1f))

                    HorizontalDivider(modifier = Modifier.padding(vertical = rDp(8.dp)))

                    // Version info
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDp(12.dp)),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.padding(rDp(12.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "MemoryVault",
                                    fontSize = rSp(12.sp),
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = ManropeFontFamily
                                )
                                Text(
                                    "v1.0.0",
                                    fontSize = rSp(10.sp),
                                    fontFamily = maple,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Outlined.Info,
                                contentDescription = null,
                                modifier = Modifier.size(rDp(16.dp)),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        content = {
            when (selectedScreen) {
                VaultScreen.DATA_EXPLORER -> {
                    VaultDataExplorerScreen(onDrawerOpen = {
                        scope.launch {
                            drawerState.open()
                        }
                    })
                }
                VaultScreen.MANAGEMENT -> VaultManagementScreen()
                VaultScreen.LOGGER -> TerminalLoggerScreen()  // New TUI-style logger
                VaultScreen.INSPECTOR -> VaultInspectorScreen()
            }
        }
    )
}

@Composable
fun VaultNavItem(
    screen: VaultScreen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    NavigationDrawerItem(
        icon = {
            Icon(
                if (isSelected) screen.selectedIcon else screen.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(22.dp))
            )
        },
        label = {
            Column {
                Text(
                    screen.title,
                    fontSize = rSp(14.sp),
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    fontFamily = ManropeFontFamily
                )
                Text(
                    screen.description,
                    fontSize = rSp(10.sp),
                    fontFamily = maple,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        selected = isSelected,
        onClick = onClick,
        shape = RoundedCornerShape(rDp(12.dp)),
        modifier = Modifier.padding(vertical = rDp(2.dp))
    )
}

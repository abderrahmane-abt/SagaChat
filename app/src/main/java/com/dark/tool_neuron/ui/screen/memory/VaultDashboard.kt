package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewmodel.memory.VaultInspectorScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultDashboard() {
    var selectedScreen by remember { mutableStateOf(VaultScreen.MANAGEMENT) }
    
    NavigationDrawer(
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(
                    topEnd = rDp(16.dp),
                    bottomEnd = rDp(16.dp)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(rDp(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDp(8.dp))
                ) {
                    Text(
                        "Vault Dashboard",
                        fontSize = rSp(20.sp),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = rDp(16.dp))
                    )
                    
                    Divider()
                    
                    Spacer(Modifier.height(rDp(8.dp)))
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Face, contentDescription = null) },
                        label = { Text("Management", fontSize = rSp(14.sp)) },
                        selected = selectedScreen == VaultScreen.MANAGEMENT,
                        onClick = { selectedScreen = VaultScreen.MANAGEMENT }
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.List, contentDescription = null) },
                        label = { Text("Logger", fontSize = rSp(14.sp)) },
                        selected = selectedScreen == VaultScreen.LOGGER,
                        onClick = { selectedScreen = VaultScreen.LOGGER }
                    )
                    
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Face, contentDescription = null) },
                        label = { Text("Inspector", fontSize = rSp(14.sp)) },
                        selected = selectedScreen == VaultScreen.INSPECTOR,
                        onClick = { selectedScreen = VaultScreen.INSPECTOR }
                    )
                    
                    Spacer(Modifier.weight(1f))
                    
                    Divider()
                    
                    Spacer(Modifier.height(rDp(8.dp)))
                    
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(rDp(12.dp)),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(rDp(12.dp)),
                            verticalArrangement = Arrangement.spacedBy(rDp(4.dp))
                        ) {
                            Text(
                                "Memory Vault",
                                fontSize = rSp(12.sp),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "v1.0.0",
                                fontSize = rSp(10.sp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    ) {
        when (selectedScreen) {
            VaultScreen.MANAGEMENT -> VaultManagementScreen()
            VaultScreen.LOGGER -> VaultLoggerScreen()
            VaultScreen.INSPECTOR -> VaultInspectorScreen()
        }
    }
}

enum class VaultScreen {
    MANAGEMENT,
    LOGGER,
    INSPECTOR
}

@Composable
fun NavigationDrawer(
    drawerContent: @Composable () -> Unit,
    content: @Composable () -> Unit
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    
    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = drawerContent,
        content = content
    )
}
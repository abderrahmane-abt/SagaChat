package com.dark.neuroverse.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.neuroverse.viewModel.PluginStoreScreenViewModel
import com.dark.plugins.model.PluginLocalDB
import com.dark.neuroverse.activity.MainActivity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginStoreScreen(
    viewModel: PluginStoreScreenViewModel = viewModel()
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { viewModel.init(context) }

    val installed by viewModel.installedPlugins.collectAsStateWithLifecycle(emptyList())
    val running by viewModel.runningPlugins.collectAsStateWithLifecycle(emptyList())
    val current by viewModel.currentPlugin.collectAsStateWithLifecycle(null)

    val runningNames by remember(running) {
        derivedStateOf { running.mapNotNull { it.api?.getPluginInfo()?.name }.toSet() }
    }
    val currentName by remember(current) {
        derivedStateOf { current?.api?.getPluginInfo()?.name }
    }

    // SAF picker for plugin files
    val addLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.addPluginFromUri(context, uri)
                Toast.makeText(context, "Installing plugin…", Toast.LENGTH_SHORT).show()
            }
        }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Plugin Store") },
                navigationIcon = {
                    IconButton(onClick = {
                        // navigate to MainActivity (home)
                        val intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }
                },
                actions = {
                    // Optional: open home too (text+icon)
                    TextButton(onClick = {
                        val intent = Intent(context, MainActivity::class.java)
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Default.Dashboard, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Home")
                    }
                },
                scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // allow zip/jar/apk
                    addLauncher.launch(arrayOf("application/zip", "application/java-archive", "application/vnd.android.package-archive", "*/*"))
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add plugin from file")
            }
        }
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to MaterialTheme.colorScheme.surface,
                        1f to MaterialTheme.colorScheme.surfaceVariant
                    )
                )
                .padding(inner)
        ) {
            if (installed.isEmpty()) {
                EmptyState(
                    onSeed = {
                        viewModel.init(context)
                        Toast.makeText(context, "Seeding demo plugins…", Toast.LENGTH_SHORT).show()
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(installed, key = { it.pluginPath }) { plugin ->
                        val isRunning = runningNames.contains(plugin.pluginName)
                        val isCurrent = currentName == plugin.pluginName

                        PluginCard(
                            plugin = plugin,
                            isRunning = isRunning,
                            isCurrent = isCurrent,
                            onRun = {
                                // Run via manager
                                viewModel.runPlugin(
                                    context,
                                    plugin.pluginName,
                                    data = mapOf("source" to "PluginStoreScreen")
                                )
                                // Launch MainActivity with plugin extra
                                val intent = Intent(context, MainActivity::class.java)
                                    .putExtra("plugin_name", plugin.pluginName)
                                context.startActivity(intent)
                            },
                            onStop = {
                                viewModel.stopPlugin(plugin.pluginName)
                            },
                            onSetCurrent = {
                                viewModel.setCurrentPluginByName(plugin.pluginName)
                            },
                            onDelete = {
                                val ok = viewModel.deletePlugin(plugin.pluginName)
                                val msg = if (ok) "Deleted ${plugin.pluginName}" else "Failed to delete ${plugin.pluginName}"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    item { Spacer(Modifier.height(56.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PluginCard(
    plugin: PluginLocalDB,
    isRunning: Boolean,
    isCurrent: Boolean,
    onRun: () -> Unit,
    onStop: () -> Unit,
    onSetCurrent: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Columnish(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = plugin.pluginName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (isCurrent) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Current") },
                        leadingIcon = { Icon(Icons.Outlined.Star, contentDescription = null) }
                    )
                }

                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                }
            }

            Spacer(Modifier.height(8.dp))

            Text("Version: ${plugin.pluginVersion}", style = MaterialTheme.typography.bodySmall)
            Text(
                text = "Path: ${plugin.pluginPath}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (isRunning) {
                    Button(onClick = onStop) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Stop")
                    }
                    OutlinedButton(onClick = onSetCurrent, enabled = !isCurrent) {
                        Text(if (isCurrent) "Already Current" else "Set Current")
                    }
                } else {
                    Button(onClick = onRun) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Run")
                    }
                    OutlinedButton(onClick = onSetCurrent, enabled = !isCurrent) {
                        Text(if (isCurrent) "Already Current" else "Set Current")
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(onSeed: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Columnish(horizontal = Alignment.CenterHorizontally) {
            Text("No plugins yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text("Tap below to (re)seed the built-in demo plugins.", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onSeed) { Text("Add Demo Plugins") }
        }
    }
}

@Composable
private fun Columnish(
    modifier: Modifier = Modifier,
    horizontal: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit
) = androidx.compose.foundation.layout.Column(
    modifier = modifier,
    horizontalAlignment = horizontal,
    content = { content() }
)

package com.dark.neuroverse.activity

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.viewModel.PluginHostViewModel
import com.dark.plugins.ui.theme.NeuroVersePluginTheme
import com.dark.plugins.worker.PluginManager

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVersePluginTheme {
                //Scaffold { padding -> PluginHostScreen(padding) }
            }
        }
    }
}


/* ===================================================================== */
/*  Composables                                                           */
/* ===================================================================== */
@SuppressLint("StateFlowValueCalledInComposition")
@Composable
fun PluginHostScreen(
    paddingValues: PaddingValues = PaddingValues(),
    viewModel: PluginHostViewModel
) {
    val ctx = LocalContext.current.applicationContext

    val loaded by viewModel.loadedPlugins.collectAsState()
    val activeName by viewModel.activePluginName.collectAsState()
    val isActive by viewModel.isActiveLoaded.collectAsState()

    /* ----------  one-shot startup work  ---------- */
    LaunchedEffect(Unit) {
        viewModel.ensureInitialLoad(ctx)
    }

    /* ----------  fallback select  ---------- */
    LaunchedEffect(loaded, activeName) {
        viewModel.selectFirstIfNone()
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Crossfade(
            targetState = if (isActive) activeName else null,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            label = "pluginCrossfade"
        ) { name ->
            if (name == null) return@Crossfade
            val storeOwner = remember(name) { viewModel.currentStoreOwner() }
            // The plugin is responsible for rendering its own content.
            // We assume PluginManager.currentPlugin exposes a Composable content lambda.
            PluginManager.currentPlugin.value?.content?.invoke()
        }

        /* Optional selector row — uncomment if you want the inline controls back. */
        /*
        Spacer(Modifier.height(12.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(
                loaded,
                key = { it.loadedPlugin?.api?.getPluginInfo()?.name ?: it.hashCode() }
            ) { plugin ->
                val name = plugin.loadedPlugin?.api?.getPluginInfo()?.name ?: "Unknown"
                val selected = name == activeName
                val bg by animateColorAsState(
                    if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant,
                    label = "chipBg"
                )
                val elev by animateDpAsState(
                    if (selected) 6.dp else 0.dp,
                    label = "chipElev"
                )

                Surface(
                    tonalElevation = elev,
                    shape = RoundedCornerShape(16.dp),
                    color = bg,
                    modifier = Modifier.size(width = 120.dp, height = 48.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // select (left half)
                        Box(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { viewModel.setCurrentByName(name) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                name.take(2).uppercase(),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        // stop (right half)
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { viewModel.stopPlugin(name) }
                                .background(MaterialTheme.colorScheme.error),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
        */
    }
}

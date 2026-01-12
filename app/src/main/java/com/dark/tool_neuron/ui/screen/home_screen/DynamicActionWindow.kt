package com.dark.tool_neuron.ui.screen.home_screen

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.CuteSwitch
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel
import java.io.RandomAccessFile

@Composable
fun DynamicActionWindow(
    chatViewModel: ChatViewModel,
    modelViewModel: LLMModelViewModel
) {
    val appState by AppStateManager.appState.collectAsState()
    var showSystemInfo by remember { mutableStateOf(false) }
    var isExpanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ),
        elevation = CardDefaults.cardElevation(rDp(4.dp)),
        colors = CardDefaults.cardColors(
            containerColor = appState.getBackgroundColor()
        ),
        shape = RoundedCornerShape(rDp(16.dp))
    ) {
        Column {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDp(16.dp)),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Status",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appState.getContentColor()
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // System info toggle
                    CuteSwitch(
                        checked = showSystemInfo,
                        onCheckedChange = { showSystemInfo = it },
                        width = rDp(44.dp),
                        height = rDp(24.dp),
                        thumbSize = rDp(18.dp)
                    )

                    Text(
                        text = "System",
                        style = MaterialTheme.typography.labelSmall,
                        color = appState.getContentColor()
                    )

                    ActionButton(
                        onClickListener = { isExpanded = !isExpanded },
                        icon = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            // Content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeIn(),
                exit = shrinkVertically(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ) + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = appState.getContentColor().copy(alpha = 0.1f))

                    AnimatedContent(
                        targetState = showSystemInfo,
                        transitionSpec = {
                            fadeIn(
                                animationSpec = tween(200)
                            ) togetherWith fadeOut(
                                animationSpec = tween(200)
                            )
                        },
                        label = "content_transition"
                    ) { showSystem ->
                        if (showSystem) {
                            SystemInfoContent(appState, modelViewModel)
                        } else {
                            StateContent(appState, chatViewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StateContent(appState: AppState, chatViewModel: ChatViewModel) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(16.dp))
    ) {
        when (appState) {
            is AppState.Welcome -> WelcomeContent()
            is AppState.NoModelLoaded -> NoModelLoadedContent(chatViewModel)
            is AppState.ModelLoaded -> ModelLoadedContent(appState)
            is AppState.LoadingModel -> LoadingModelContent(appState)
            is AppState.GeneratingText -> GeneratingTextContent(appState, chatViewModel)
            is AppState.GeneratingImage -> GeneratingImageContent(appState, chatViewModel)
            is AppState.GeneratingAudio -> GeneratingAudioContent(appState)
            is AppState.Error -> ErrorContent(appState)
        }
    }
}

@Composable
private fun SystemInfoContent(appState: AppState, modelViewModel: LLMModelViewModel) {
    val context = LocalContext.current
    val installedModels by modelViewModel.installedModels.collectAsState(initial = emptyList())
    val isTextModelLoaded by modelViewModel.isGgufModelLoaded.collectAsState()
    val isImageModelLoaded by modelViewModel.isDiffusionModelLoaded.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDp(16.dp))
            .heightIn(max = rDp(300.dp))
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        // Loaded Models Section
        Text(
            text = "Loaded Models",
            style = MaterialTheme.typography.labelMedium,
            color = appState.getColor(),
            fontWeight = FontWeight.SemiBold
        )

        if (isTextModelLoaded || isImageModelLoaded) {
            Column(verticalArrangement = Arrangement.spacedBy(rDp(8.dp))) {
                if (isTextModelLoaded) {
                    ModelStatusRow(
                        label = "Text Model",
                        status = "Active",
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (isImageModelLoaded) {
                    ModelStatusRow(
                        label = "Image Model",
                        status = "Active",
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        } else {
            Text(
                text = "No models loaded",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(rDp(4.dp)))

        // System Resources
        Text(
            text = "System Resources",
            style = MaterialTheme.typography.labelMedium,
            color = appState.getColor(),
            fontWeight = FontWeight.SemiBold
        )

        SystemMetricRow(
            icon = Icons.Default.Memory,
            label = "RAM",
            value = getMemoryUsage(context)
        )

        SystemMetricRow(
            icon = Icons.Default.Storage,
            label = "CPU Cores",
            value = getCpuCores()
        )

        SystemMetricRow(
            icon = Icons.Default.Speed,
            label = "Threads",
            value = getActiveThreads()
        )

        Spacer(modifier = Modifier.height(rDp(4.dp)))

        // Device Info
        Text(
            text = "Device Info",
            style = MaterialTheme.typography.labelMedium,
            color = appState.getColor(),
            fontWeight = FontWeight.SemiBold
        )

        InfoRow("Model", Build.MODEL)
        InfoRow("Android", Build.VERSION.RELEASE)
        InfoRow("SDK", Build.VERSION.SDK_INT.toString())
    }
}

@Composable
private fun ModelStatusRow(label: String, status: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(rDp(8.dp)))
            .padding(horizontal = rDp(12.dp), vertical = rDp(8.dp)),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(rDp(8.dp))
                    .background(color, shape = androidx.compose.foundation.shape.CircleShape)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
    }
}

@Composable
private fun SystemMetricRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(rDp(18.dp)),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium
            )
        }
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium
        )
    }
}

// State-specific content composables

@Composable
private fun WelcomeContent() {
    val appState by AppStateManager.appState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.user),
            contentDescription = null,
            modifier = Modifier.size(rDp(36.dp)),
            tint = appState.getColor()
        )
        Text(
            text = "Welcome",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = appState.getContentColor()
        )
        Text(
            text = "Load a model to begin",
            style = MaterialTheme.typography.bodySmall,
            color = appState.getContentColor().copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun NoModelLoadedContent(chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.vl_models),
            contentDescription = null,
            modifier = Modifier.size(rDp(36.dp)),
            tint = appState.getColor()
        )
        Text(
            text = "No Model Loaded",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = appState.getContentColor()
        )
        Text(
            text = "Select a model to continue",
            style = MaterialTheme.typography.bodySmall,
            color = appState.getContentColor().copy(alpha = 0.7f)
        )

        Button(
            onClick = { chatViewModel.showModelList() },
            colors = ButtonDefaults.buttonColors(
                containerColor = appState.getColor()
            )
        ) {
            Icon(Icons.Default.ModelTraining, contentDescription = null)
            Spacer(modifier = Modifier.width(rDp(8.dp)))
            Text("Select Model")
        }
    }
}

@Composable
private fun ModelLoadedContent(state: AppState.ModelLoaded) {
    val appState by AppStateManager.appState.collectAsState()

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(R.drawable.smart_temp_message),
                contentDescription = null,
                modifier = Modifier.size(rDp(32.dp)),
                tint = appState.getColor()
            )
            Column {
                Text(
                    text = "Ready",
                    style = MaterialTheme.typography.labelSmall,
                    color = appState.getContentColor().copy(alpha = 0.7f)
                )
                Text(
                    text = state.modelName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = appState.getColor(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Ready",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(rDp(20.dp))
        )
    }
}

@Composable
private fun LoadingModelContent(state: AppState.LoadingModel) {
    val appState by AppStateManager.appState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(rDp(32.dp)),
                    color = appState.getColor(),
                    strokeWidth = rDp(3.dp)
                )

                Column {
                    Text(
                        text = "Loading",
                        style = MaterialTheme.typography.labelSmall,
                        color = appState.getContentColor().copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appState.getContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = appState.getColor()
            )
        }

        LinearProgressIndicator(
            progress = { state.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(6.dp))
                .clip(RoundedCornerShape(rDp(3.dp))),
            color = appState.getColor(),
            trackColor = appState.getColor().copy(alpha = 0.2f)
        )
    }
}

@Composable
private fun GeneratingTextContent(state: AppState.GeneratingText, chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()
    val streamingText by chatViewModel.streamingAssistantMessage.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "generating")
                val rotation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "rotation"
                )

                Icon(
                    painter = painterResource(R.drawable.tool),
                    contentDescription = null,
                    modifier = Modifier
                        .size(rDp(32.dp))
                        .rotate(rotation),
                    tint = appState.getColor()
                )

                Column {
                    Text(
                        text = "Generating",
                        style = MaterialTheme.typography.labelSmall,
                        color = appState.getContentColor().copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appState.getContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (streamingText.isNotEmpty()) {
                val tokenCount = streamingText.split("\\s+".toRegex()).size
                Text(
                    text = "$tokenCount",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = appState.getColor()
                )
            }
        }

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(4.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = appState.getColor()
        )

        if (streamingText.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(rDp(8.dp)),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            ) {
                Text(
                    text = streamingText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(rDp(12.dp))
                        .heightIn(max = rDp(100.dp))
                        .verticalScroll(rememberScrollState()),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun GeneratingImageContent(state: AppState.GeneratingImage, chatViewModel: ChatViewModel) {
    val appState by AppStateManager.appState.collectAsState()
    val streamingImage by chatViewModel.streamingImage.collectAsState()
    val progress by chatViewModel.imageGenerationProgress.collectAsState()
    val step by chatViewModel.imageGenerationStep.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "generating_image")
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(800, easing = FastOutSlowInEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "scale"
                )

                Icon(
                    painter = painterResource(R.drawable.tool),
                    contentDescription = null,
                    modifier = Modifier
                        .size(rDp(32.dp))
                        .scale(scale),
                    tint = appState.getColor()
                )

                Column {
                    Text(
                        text = if (step.isNotEmpty()) step else "Creating",
                        style = MaterialTheme.typography.labelSmall,
                        color = appState.getContentColor().copy(alpha = 0.7f)
                    )
                    Text(
                        text = state.modelName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = appState.getContentColor(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = appState.getColor()
            )
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(6.dp))
                .clip(RoundedCornerShape(rDp(3.dp))),
            color = appState.getColor(),
            trackColor = appState.getColor().copy(alpha = 0.2f)
        )

        streamingImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Preview",
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(rDp(12.dp)))
            )
        }
    }
}

@Composable
private fun GeneratingAudioContent(state: AppState.GeneratingAudio) {
    val appState by AppStateManager.appState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        AudioWaveAnimation(appState.getColor())

        Text(
            text = "Generating Audio",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = appState.getContentColor()
        )

        LinearProgressIndicator(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(4.dp))
                .clip(RoundedCornerShape(rDp(2.dp))),
            color = appState.getColor()
        )
    }
}

@Composable
private fun AudioWaveAnimation(color: androidx.compose.ui.graphics.Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "audio_wave")

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDp(4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(rDp(32.dp))
    ) {
        repeat(5) { index ->
            val height by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        durationMillis = 500,
                        delayMillis = index * 80,
                        easing = FastOutSlowInEasing
                    ),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "wave_$index"
            )

            Box(
                modifier = Modifier
                    .width(rDp(4.dp))
                    .fillMaxHeight(height)
                    .clip(RoundedCornerShape(rDp(2.dp)))
                    .background(color)
            )
        }
    }
}

@Composable
private fun ErrorContent(state: AppState.Error) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDp(12.dp)),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            painter = painterResource(R.drawable.error),
            contentDescription = null,
            modifier = Modifier.size(rDp(32.dp)),
            tint = MaterialTheme.colorScheme.error
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )

            Spacer(modifier = Modifier.height(rDp(4.dp)))

            Text(
                text = state.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.modelName?.let { modelName ->
                Spacer(modifier = Modifier.height(rDp(4.dp)))
                Text(
                    text = modelName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }
}

// System info helper functions

@Composable
private fun getMemoryUsage(context: Context): String {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memoryInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memoryInfo)

    val usedMemory = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
    val totalMemory = memoryInfo.totalMem / (1024 * 1024)

    return "${usedMemory}MB / ${totalMemory}MB"
}

@Composable
private fun getCpuCores(): String {
    return "${Runtime.getRuntime().availableProcessors()}"
}

@Composable
private fun getActiveThreads(): String {
    return "${Thread.activeCount()}"
}

@Composable
private fun getCpuUsage(): String {
    return try {
        val reader = RandomAccessFile("/proc/stat", "r")
        val load = reader.readLine()
        reader.close()
        val toks = load.split(" +".toRegex())
        val idle = toks[4].toLong()
        val cpu = toks[1].toLong() + toks[2].toLong() + toks[3].toLong()
        val total = idle + cpu
        val usage = if (total > 0) ((cpu.toFloat() / total) * 100).toInt() else 0
        "$usage%"
    } catch (e: Exception) {
        "N/A"
    }
}
package com.dark.tool_neuron.ui.screens.modelScreen

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.ai_module.model.ModelData
import com.dark.ai_module.model.ModelProvider
import com.dark.ai_module.model.ModelType
import com.dark.tool_neuron.model.GGUFModels
import com.dark.tool_neuron.viewModel.ModelScreenViewModel
import com.dark.tool_neuron.viewModel.modelScreen.OnlineModelStoreViewModel
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GGUFModelScreen(
    viewModel: OnlineModelStoreViewModel = viewModel(),
    modelScreenViewModel: ModelScreenViewModel = viewModel()
) {
    val models = viewModel.ggufModels.collectAsStateWithLifecycle()
    val downloadStates = modelScreenViewModel.downloadStates.collectAsStateWithLifecycle()
    val installedModels = modelScreenViewModel.models.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {
        when {
            models.value.isEmpty() -> {
                EmptyState()
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(models.value) { model ->
                        val isInstalled = installedModels.value.any {
                            it.modelName == model.modelName
                        }
                        val downloadState = downloadStates.value[model.modelFileLink]
                        val compatibility = getModelCompatibility(context, model)

                        ModelCard(
                            model = model,
                            compatibility = compatibility,
                            isInstalled = isInstalled,
                            downloadState = downloadState,
                            onDownload = {
                                val modelData = model.toModelData(context)
                                modelScreenViewModel.startDownload(modelData, context)
                            },
                            onCancelDownload = {
                                modelScreenViewModel.cancelDownload(
                                    model.modelName,
                                    model.modelFileLink
                                )
                            },
                            onDelete = {
                                modelScreenViewModel.removeModel(model.modelName)
                            }
                        )
                    }
                }
            }
        }
    }
}

data class ModelCompatibility(
    val score: Int, // 0-100
    val rating: CompatibilityRating,
    val ramRequirement: String,
    val storageRequirement: String,
    val deviceRam: String,
    val availableStorage: String,
    val recommendations: List<String>,
    val warnings: List<String>
)

enum class CompatibilityRating {
    EXCELLENT, GOOD, FAIR, POOR, INCOMPATIBLE
}

fun getModelCompatibility(context: Context, model: GGUFModels): ModelCompatibility {
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val memInfo = ActivityManager.MemoryInfo()
    activityManager.getMemoryInfo(memInfo)

    val totalRamGB = memInfo.totalMem / (1024.0 * 1024.0 * 1024.0)
    val availableRamGB = memInfo.availMem / (1024.0 * 1024.0 * 1024.0)

    // Parse model size (assuming format like "4.2GB", "500MB")
    val modelSizeGB = parseModelSize(model.modelFileSize)

    // Estimate RAM requirement (model size + overhead)
    val estimatedRamGB = modelSizeGB * 1.5 // 50% overhead for processing

    // Get available storage
    val availableStorageGB = context.filesDir.usableSpace / (1024.0 * 1024.0 * 1024.0)

    // Calculate compatibility score
    var score = 100
    val recommendations = mutableListOf<String>()
    val warnings = mutableListOf<String>()

    // Check RAM
    when {
        estimatedRamGB > totalRamGB -> {
            score -= 60
            warnings.add("Model requires more RAM than available")
        }
        estimatedRamGB > availableRamGB * 0.8 -> {
            score -= 30
            warnings.add("May experience memory pressure")
            recommendations.add("Close other apps before running")
        }
        estimatedRamGB > availableRamGB * 0.5 -> {
            score -= 15
            recommendations.add("Moderate RAM usage expected")
        }
        else -> {
            recommendations.add("Excellent RAM availability")
        }
    }

    // Check storage
    when {
        modelSizeGB > availableStorageGB -> {
            score -= 40
            warnings.add("Insufficient storage space")
        }
        modelSizeGB > availableStorageGB * 0.8 -> {
            score -= 20
            warnings.add("Low storage space")
        }
    }

    // Check context size
    when {
        model.ctxSize > 8192 -> {
            score -= 10
            recommendations.add("Large context size - may be slower")
        }
        model.ctxSize > 4096 -> {
            recommendations.add("Standard context size")
        }
    }

    // Check GPU layers
    if (model.gpuLayers > 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            recommendations.add("GPU acceleration available")
            score += 10
        } else {
            warnings.add("GPU layers set but may not be supported")
            score -= 5
        }
    }

    // Determine rating
    val rating = when {
        score >= 85 -> CompatibilityRating.EXCELLENT
        score >= 70 -> CompatibilityRating.GOOD
        score >= 50 -> CompatibilityRating.FAIR
        score >= 30 -> CompatibilityRating.POOR
        else -> CompatibilityRating.INCOMPATIBLE
    }

    return ModelCompatibility(
        score = score.coerceIn(0, 100),
        rating = rating,
        ramRequirement = "${"%.1f".format(estimatedRamGB)} GB",
        storageRequirement = model.modelFileSize,
        deviceRam = "${"%.1f".format(totalRamGB)} GB",
        availableStorage = "${"%.1f".format(availableStorageGB)} GB",
        recommendations = recommendations,
        warnings = warnings
    )
}

fun parseModelSize(sizeStr: String): Double {
    val cleaned = sizeStr.trim().uppercase()
    return when {
        cleaned.endsWith("GB") -> cleaned.removeSuffix("GB").trim().toDoubleOrNull() ?: 0.0
        cleaned.endsWith("MB") -> (cleaned.removeSuffix("MB").trim().toDoubleOrNull() ?: 0.0) / 1024.0
        else -> 0.0
    }
}

@Composable
fun ModelCard(
    model: GGUFModels,
    compatibility: ModelCompatibility,
    isInstalled: Boolean,
    downloadState: com.dark.tool_neuron.model.DownloadState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.modelName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = model.architecture.uppercase(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = model.modelFileSize,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                ModelTypeChip(modelType = model.modelType)
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Compatibility Badge
            CompatibilityBadge(compatibility = compatibility)

            Spacer(modifier = Modifier.height(16.dp))

            // Stats Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatItem(
                    icon = Icons.Default.TextFields,
                    label = "CTX SIZE",
                    value = formatNumber(model.ctxSize),
                    modifier = Modifier.weight(1f)
                )
                StatItem(
                    icon = Icons.Outlined.Layers,
                    label = "GPU LAYERS",
                    value = model.gpuLayers.toString(),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Additional Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (model.isToolCalling) {
                        InfoChip(text = "Tool Calling")
                    }
                    if (model.useMMAP) {
                        InfoChip(text = "MMAP")
                    }
                    if (model.useMLOCK) {
                        InfoChip(text = "MLOCK")
                    }
                }

                // Show details toggle
                TextButton(
                    onClick = { showDetails = !showDetails },
                    contentPadding = PaddingValues(8.dp)
                ) {
                    Text(
                        text = if (showDetails) "Less" else "Details",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Icon(
                        imageVector = if (showDetails) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // Expanded Details
            if (showDetails) {
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
                Spacer(modifier = Modifier.height(16.dp))

                DetailedSpecs(model = model, compatibility = compatibility)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Download/Install Button or Progress
            when {
                downloadState?.isDownloading == true -> {
                    DownloadProgressSection(
                        progress = downloadState.progress,
                        onCancel = onCancelDownload
                    )
                }
                downloadState?.isComplete == true || isInstalled -> {
                    InstalledSection(onDelete = onDelete)
                }
                downloadState?.errorMessage != null -> {
                    ErrorSection(
                        errorMessage = downloadState.errorMessage!!,
                        onRetry = onDownload
                    )
                }
                else -> {
                    Button(
                        onClick = onDownload,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        enabled = compatibility.rating != CompatibilityRating.INCOMPATIBLE
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (compatibility.rating == CompatibilityRating.INCOMPATIBLE)
                                "Incompatible"
                            else
                                "Download Model"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CompatibilityBadge(compatibility: ModelCompatibility) {
    val (color, icon, text) = when (compatibility.rating) {
        CompatibilityRating.EXCELLENT -> Triple(
            MaterialTheme.colorScheme.primaryContainer,
            Icons.Default.CheckCircle,
            "Excellent Match"
        )
        CompatibilityRating.GOOD -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            Icons.Default.ThumbUp,
            "Good Match"
        )
        CompatibilityRating.FAIR -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            Icons.Default.Info,
            "Fair Match"
        )
        CompatibilityRating.POOR -> Triple(
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
            Icons.Default.Warning,
            "Poor Match"
        )
        CompatibilityRating.INCOMPATIBLE -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            Icons.Default.Error,
            "Incompatible"
        )
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "${compatibility.score}%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DetailedSpecs(model: GGUFModels, compatibility: ModelCompatibility) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "System Requirements",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        // Requirements comparison
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RequirementRow(
                label = "RAM Required",
                required = compatibility.ramRequirement,
                available = compatibility.deviceRam,
                isSufficient = compatibility.score >= 50
            )
            RequirementRow(
                label = "Storage Required",
                required = compatibility.storageRequirement,
                available = compatibility.availableStorage,
                isSufficient = parseModelSize(compatibility.storageRequirement) <=
                        parseModelSize(compatibility.availableStorage)
            )
        }

        // Sampling parameters
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Sampling Parameters",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpecItem(
                label = "Temperature",
                value = model.temp.toString(),
                modifier = Modifier.weight(1f)
            )
            SpecItem(
                label = "Top-P",
                value = model.topP.toString(),
                modifier = Modifier.weight(1f)
            )
            SpecItem(
                label = "Top-K",
                value = model.topK.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SpecItem(
                label = "Max Tokens",
                value = formatNumber(model.maxTokens),
                modifier = Modifier.weight(1f)
            )
            SpecItem(
                label = "Mirostat",
                value = model.mirostat.toString(),
                modifier = Modifier.weight(1f)
            )
        }

        // Recommendations & Warnings
        if (compatibility.recommendations.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Recommendations",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            compatibility.recommendations.forEach { rec ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Light,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = rec,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        if (compatibility.warnings.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Warnings",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
            compatibility.warnings.forEach { warning ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
fun RequirementRow(
    label: String,
    required: String,
    available: String,
    isSufficient: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$required / $available",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (isSufficient)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
            Icon(
                imageVector = if (isSufficient) Icons.Default.CheckCircle else Icons.Default.Error,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = if (isSufficient)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.error
            )
        }
    }
}

@Composable
fun SpecItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

fun formatNumber(num: Int): String {
    return when {
        num >= 1000 -> "${(num / 1000.0).roundToInt()}K"
        else -> num.toString()
    }
}

@Composable
fun DownloadProgressSection(
    progress: Float,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            PixelProgressBar(
                progress = progress,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Cancel Download")
        }
    }
}

@Composable
fun PixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_animation")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    Canvas(modifier = modifier) {
        val barWidth = size.width * progress
        val pixelSize = 4.dp.toPx()
        val pixelGap = 1.dp.toPx()
        val totalPixelSize = pixelSize + pixelGap

        val numPixelsX = (barWidth / totalPixelSize).toInt()
        val numPixelsY = (size.height / totalPixelSize).toInt().coerceAtLeast(1)

        for (y in 0 until numPixelsY) {
            for (x in 0 until numPixelsX) {
                val xPos = x * totalPixelSize
                val yPos = y * totalPixelSize

                val normalizedX = x.toFloat() / numPixelsX.coerceAtLeast(1)

                val alpha = if (normalizedX > shimmerOffset - 0.2f &&
                    normalizedX < shimmerOffset) {
                    1f
                } else {
                    0.7f
                }

                drawRect(
                    color = Color(0xFF6750A4).copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(xPos, yPos),
                    size = androidx.compose.ui.geometry.Size(pixelSize, pixelSize)
                )
            }
        }
    }
}

@Composable
fun InstalledSection(onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer
            )
        }
    }
}

@Composable
fun ErrorSection(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Retry Download")
        }
    }
}
@Composable
fun StatItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
fun ModelTypeChip(modelType: String) {
    val color = when (modelType) {
        "TXT" -> MaterialTheme.colorScheme.primaryContainer
        "VLM" -> MaterialTheme.colorScheme.secondaryContainer
        "EMBED" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = when (modelType) {
        "TXT" -> MaterialTheme.colorScheme.onPrimaryContainer
        "VLM" -> MaterialTheme.colorScheme.onSecondaryContainer
        "EMBED" -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = modelType,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = textColor
        )
    }
}

@Composable
fun InfoChip(text: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.CloudDownload,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No models available",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Models will appear here when available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

// Extension function to convert GGUFModels to ModelData
fun GGUFModels.toModelData(context: Context): ModelData {
    val modelsDir = File(context.filesDir, "models")
    val modelFile = File(modelsDir, modelName)

    return ModelData(
        modelName = modelName,
        providerName = ModelProvider.LocalGGUF.toString(),
        modelType = when (modelType) {
            "TXT" -> ModelType.TEXT
            "VLM" -> ModelType.VLM
            "EMBED" -> ModelType.EMBEDDING
            else -> ModelType.TEXT
        },
        modelPath = modelFile.absolutePath,
        architecture = architecture,
        gpuLayers = gpuLayers,
        useMMAP = useMMAP,
        useMLOCK = useMLOCK,
        ctxSize = ctxSize,
        temp = temp,
        topK = topK,
        topP = topP,
        minP = minP,
        maxTokens = maxTokens,
        mirostat = mirostat,
        mirostatTau = mirostatTau,
        mirostatEta = mirostatEta,
        seed = seed,
        isImported = isImported,
        modelUrl = modelFileLink,
        isToolCalling = isToolCalling,
        systemPrompt = systemPrompt,
        chatTemplate = chatTemplate
    )
}
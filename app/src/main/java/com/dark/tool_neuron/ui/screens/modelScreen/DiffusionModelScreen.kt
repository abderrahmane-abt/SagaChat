package com.dark.tool_neuron.ui.screens.modelScreen

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.activity.formatBytes
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.modelScreen.OnlineModelStoreViewModel
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.workers.DownloadState

@Composable
fun DiffusionModelScreen(
    viewModel: OnlineModelStoreViewModel = viewModel()
) {
    val remoteDiffusionModels by viewModel.remoteDiffusionModels.collectAsStateWithLifecycle()
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle()
    val downloadsState by viewModel.downloadsState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        when {
            remoteDiffusionModels.isEmpty() -> {
                DiffusionEmptyState()
            }
            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(rDP(16.dp)),
                    verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
                ) {
                    items(
                        items = remoteDiffusionModels,
                        key = { it.modelFileLink }
                    ) { cloudModel ->
                        // Check if model is installed
                        val isInstalled = installedModels.any {
                            it.modelName == cloudModel.modelName
                        }

                        // Get download state
                        val downloadState = downloadsState.getDownload(cloudModel.modelFileLink)

                        DiffusionModelCard(
                            model = cloudModel,
                            isInstalled = isInstalled,
                            downloadState = downloadState,
                            onDownload = {
                                viewModel.downloadModel(cloudModel)
                            },
                            onCancelDownload = {
                                viewModel.cancelDownload(cloudModel.modelFileLink)
                            },
                            onDelete = {
                                // Find the installed model ID
                                val installedModel = installedModels.find {
                                    it.modelName == cloudModel.modelName
                                }
                                installedModel?.let {
                                    viewModel.deleteModel(it.id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiffusionModelCard(
    model: CloudModel,
    isInstalled: Boolean,
    downloadState: DownloadState?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(16.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(2.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(20.dp))
        ) {
            // Header Section
            DiffusionModelHeader(model = model)

            Spacer(modifier = Modifier.height(rDP(16.dp)))

            // Model Info Row
            DiffusionModelInfo(model = model)

            Spacer(modifier = Modifier.height(rDP(16.dp)))

            // Action Section
            DiffusionModelActionSection(
                downloadState = downloadState,
                isInstalled = isInstalled,
                onDownload = onDownload,
                onCancelDownload = onCancelDownload,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun DiffusionModelHeader(model: CloudModel) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Model Icon
        Box(
            modifier = Modifier
                .size(rDP(48.dp))
                .clip(RoundedCornerShape(rDP(12.dp)))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Image,
                contentDescription = null,
                modifier = Modifier.size(rDP(28.dp)),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }

        // Model Name & Description
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = model.modelName,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize)
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (model.modelDescription.isNotEmpty()) {
                Spacer(modifier = Modifier.height(rDP(4.dp)))
                Text(
                    text = model.modelDescription,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun DiffusionModelInfo(model: CloudModel) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        // Provider Type
        InfoChip(
            label = "Provider",
            value = model.providerName,
            modifier = Modifier.weight(1f)
        )

        // File Size
        InfoChip(
            label = "Size",
            value = model.modelFileSize,
            modifier = Modifier.weight(1f)
        )

        // CPU/GPU Info
        val runOnCpu = model.metaData["run-on-cpu"]?.toBoolean() ?: false
        InfoChip(
            label = "Device",
            value = if (runOnCpu) "CPU" else "GPU",
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(rDP(10.dp)))
            .background(MaterialTheme.colorScheme.surface)
            .padding(rDP(12.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(4.dp))
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
            ),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun DiffusionModelActionSection(
    downloadState: DownloadState?,
    isInstalled: Boolean,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDelete: () -> Unit
) {
    when (downloadState) {
        is DownloadState.Downloading -> {
            DiffusionDownloadProgress(
                progress = downloadState.progressPercent.toFloat(),
                downloadedBytes = downloadState.downloadedBytes,
                totalBytes = downloadState.totalBytes,
                onCancel = onCancelDownload
            )
        }

        is DownloadState.Installing -> {
            DiffusionInstallingSection()
        }

        is DownloadState.Completed, null -> {
            if (isInstalled) {
                DiffusionInstalledSection(onDelete = onDelete)
            } else {
                DiffusionDownloadButton(onDownload = onDownload)
            }
        }

        is DownloadState.Failed -> {
            DiffusionErrorSection(
                errorMessage = downloadState.error,
                onRetry = onDownload
            )
        }

        is DownloadState.Cancelled -> {
            DiffusionDownloadButton(onDownload = onDownload)
        }

        else -> { /* Idle state */ }
    }
}

@Composable
private fun DiffusionDownloadButton(onDownload: () -> Unit) {
    Button(
        onClick = onDownload,
        modifier = Modifier
            .fillMaxWidth()
            .height(rDP(52.dp)),
        shape = RoundedCornerShape(rDP(12.dp)),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = rDP(0.dp)
        )
    ) {
        Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(rDP(20.dp))
        )
        Spacer(modifier = Modifier.width(rDP(10.dp)))
        Text(
            text = "Download Model",
            style = MaterialTheme.typography.labelLarge.copy(
                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
            ),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun DiffusionDownloadProgress(
    progress: Float,
    downloadedBytes: Long,
    totalBytes: Long,
    onCancel: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Downloading...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                ),
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${progress.toInt()}%",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (totalBytes > 0) {
                    Text(
                        text = "${formatBytes(downloadedBytes)} / ${formatBytes(totalBytes)}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(rDP(12.dp))
                .clip(RoundedCornerShape(rDP(6.dp)))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            DiffusionPixelProgressBar(
                progress = progress / 100f,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(rDP(12.dp)))

        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp)),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = null,
                modifier = Modifier.size(rDP(20.dp))
            )
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text(
                text = "Cancel Download",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                )
            )
        }
    }
}

@Composable
private fun DiffusionPixelProgressBar(
    progress: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_animation")
    val progressColor = MaterialTheme.colorScheme.primary

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
                    color = progressColor.copy(alpha = alpha),
                    topLeft = androidx.compose.ui.geometry.Offset(xPos, yPos),
                    size = androidx.compose.ui.geometry.Size(pixelSize, pixelSize)
                )
            }
        }
    }
}

@Composable
private fun DiffusionInstallingSection() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(12.dp)),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(rDP(16.dp)),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(rDP(20.dp)),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                strokeWidth = rDP(2.dp)
            )
            Spacer(modifier = Modifier.width(rDP(12.dp)))
            Text(
                text = "Installing Model...",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                ),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun DiffusionInstalledSection(onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(rDP(12.dp)),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier.padding(rDP(16.dp)),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(rDP(20.dp))
                )
                Spacer(modifier = Modifier.width(rDP(8.dp)))
                Text(
                    text = "Installed",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        IconButton(
            onClick = onDelete,
            modifier = Modifier
                .size(rDP(48.dp))
                .clip(RoundedCornerShape(rDP(12.dp)))
                .background(MaterialTheme.colorScheme.errorContainer)
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Delete",
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(rDP(20.dp))
            )
        }
    }
}

@Composable
private fun DiffusionErrorSection(
    errorMessage: String,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp)),
            color = MaterialTheme.colorScheme.errorContainer
        ) {
            Row(
                modifier = Modifier.padding(rDP(12.dp)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(rDP(20.dp))
                )
                Spacer(modifier = Modifier.width(rDP(8.dp)))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                    ),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(rDP(12.dp))
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(rDP(20.dp))
            )
            Spacer(modifier = Modifier.width(rDP(8.dp)))
            Text(
                text = "Retry Download",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                )
            )
        }
    }
}

@Composable
private fun DiffusionEmptyState() {
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
                modifier = Modifier.size(rDP(64.dp)),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(rDP(16.dp)))
            Text(
                text = "No diffusion models available",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Models will appear here when available",
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = rSp(MaterialTheme.typography.bodySmall.fontSize)
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
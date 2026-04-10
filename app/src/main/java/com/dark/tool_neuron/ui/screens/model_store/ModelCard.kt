package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.model.ui.ActionIcon
import com.dark.tool_neuron.model.ui.ActionItem
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionProgressButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun CatalogModelCard(
    model: HuggingFaceModel,
    isInstalled: Boolean,
    downloadState: HxdState?,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    val isActive = downloadState != null &&
        downloadState.status in listOf(HxdStatus.QUEUED, HxdStatus.CONNECTING, HxdStatus.DOWNLOADING)
    val isFailed = downloadState != null && downloadState.status == HxdStatus.FAILED

    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(dimens.spacingMd)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    TnIcons.Sparkles, null,
                    Modifier.size(18.dp),
                    MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(dimens.spacingSm))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = model.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(dimens.spacingSm))
                DownloadAction(
                    isInstalled = isInstalled,
                    isDownloading = isActive,
                    isFailed = isFailed,
                    onDownload = onDownload,
                    onCancel = onCancel,
                )
            }

            Spacer(Modifier.height(dimens.spacingSm))

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                InfoChip(model.approximateSize, MaterialTheme.colorScheme.primaryContainer)
                if (model.quantization.isNotBlank()) {
                    InfoChip(model.quantization, MaterialTheme.colorScheme.tertiaryContainer)
                }
                model.tags.forEach { tag ->
                    InfoChip(tag, MaterialTheme.colorScheme.surfaceContainerHigh)
                }
            }

            AnimatedVisibility(visible = isActive) {
                if (downloadState != null) {
                    DownloadProgress(downloadState)
                }
            }

            AnimatedVisibility(visible = isFailed) {
                Text(
                    text = "Download failed: ${downloadState?.error ?: "Unknown error"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = dimens.spacingXs)
                )
            }
        }
    }
}

@Composable
private fun DownloadAction(
    isInstalled: Boolean,
    isDownloading: Boolean,
    isFailed: Boolean,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
) {
    when {
        isInstalled -> {
            ActionButton(
                onClickListener = {},
                icon = TnIcons.Check,
                contentDescription = "Installed"
            )
        }
        isDownloading -> {
            ActionProgressButton(
                onClickListener = onCancel,
                contentDescription = "Cancel"
            )
        }
        isFailed -> {
            ActionButton(
                onClickListener = onDownload,
                icon = TnIcons.Refresh,
                contentDescription = "Retry"
            )
        }
        else -> {
            ActionButton(
                onClickListener = onDownload,
                icon = TnIcons.Download,
                contentDescription = "Download"
            )
        }
    }
}

@Composable
private fun DownloadProgress(state: HxdState) {
    val dimens = LocalDimens.current
    Column(modifier = Modifier.padding(top = dimens.spacingSm)) {
        val progress = state.progress
        if (progress >= 0f) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val statusText = when (state.status) {
                HxdStatus.QUEUED -> "Queued"
                HxdStatus.CONNECTING -> "Connecting..."
                HxdStatus.DOWNLOADING -> {
                    val dl = formatBytes(state.downloadedBytes)
                    val total = if (state.totalBytes > 0) " / ${formatBytes(state.totalBytes)}" else ""
                    "$dl$total"
                }
                else -> ""
            }
            Text(statusText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.speedFormatted.isNotBlank()) {
                Text(state.speedFormatted, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun InfoChip(text: String, color: Color) {
    Surface(shape = LocalTnShapes.current.chip, color = color) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

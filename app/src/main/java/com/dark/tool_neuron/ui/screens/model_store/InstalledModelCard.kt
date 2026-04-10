package com.dark.tool_neuron.ui.screens.model_store

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.ui.ActionIcon
import com.dark.tool_neuron.model.ui.ActionItem
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.MultiActionButton
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun InstalledModelCard(
    model: ModelInfo,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current

    Surface(
        shape = shapes.card,
        color = if (model.isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
        else MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .clickable(onClick = if (model.isActive) onUnload else onLoad)
                .padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = TnIcons.Sparkles,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (model.isActive) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.width(dimens.spacingSm))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = model.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = formatModelSize(model.fileSize),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (model.pathType == PathType.CONTENT_URI) "Local" else "Downloaded",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (model.isActive) {
                Surface(shape = shapes.full, color = MaterialTheme.colorScheme.primary) {
                    Text(
                        "Active",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
                Spacer(Modifier.width(dimens.spacingSm))
            }

            MultiActionButton(
                actions = buildList {
                    if (!model.isActive) {
                        add(ActionItem(
                            icon = ActionIcon.Vector(TnIcons.Leaf),
                            onClick = onLoad,
                            contentDescription = "Load"
                        ))
                    }
                    add(ActionItem(
                        icon = ActionIcon.Vector(TnIcons.Trash),
                        onClick = onDelete,
                        contentDescription = "Delete"
                    ))
                }
            )
        }
    }
}

private fun formatModelSize(bytes: Long): String = when {
    bytes <= 0 -> "Unknown"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024L * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.2f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
}

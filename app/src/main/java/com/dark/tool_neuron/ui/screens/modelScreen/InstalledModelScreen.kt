package com.dark.tool_neuron.ui.screens.modelScreen

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.twotone.Inventory
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.llm_model.ModelScreenViewModel
import com.mp.ai_engine.models.llm_models.CloudModel

@Composable
fun InstalledModelsTab(viewModel: ModelScreenViewModel = viewModel()) {
    val models by viewModel.cloudModels.collectAsState()

    if (models.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = Icons.TwoTone.Inventory,
                title = "No installed models",
                subtitle = "Download or import models to get started"
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(rDP(16.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
        ) {
            items(models, key = { it.id }) { model ->
                InstalledModelCard(
                    model = model, onDelete = {

                    })
            }
        }
    }
}

@Composable
private fun InstalledModelCard(
    model: CloudModel, onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(rDP(12.dp)),
        elevation = CardDefaults.cardElevation(defaultElevation = rDP(0.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDP(16.dp)),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
            ) {
                Text(
                    text = model.modelName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.titleMedium.fontSize),
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )

                ModelTypeChip(
                     isLocal = model.isLocal
                )
            }

            IconButton(
                onClick = { showDeleteDialog = true }, modifier = Modifier.size(rDP(40.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(rDP(24.dp))
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteModelDialog(
            modelName = model.modelName,
            onDismiss = { showDeleteDialog = false },
            onConfirm = {
                onDelete()
                showDeleteDialog = false
            })
    }
}

@Composable
private fun ModelTypeChip(isLocal: Boolean) {
    val backgroundColor = if (isLocal) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
    }

    val textColor = if (isLocal) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondary
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(rDP(6.dp)))
            .background(backgroundColor)
            .padding(horizontal = rDP(10.dp), vertical = rDP(5.dp)),
        horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isLocal) Icons.Default.Storage else Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(rDP(14.dp)),
            tint = textColor
        )
        Text(
            text = if (isLocal) "Local" else "Cloud", style = MaterialTheme.typography.labelSmall.copy(
                fontSize = rSp(MaterialTheme.typography.labelSmall.fontSize)
            ), fontWeight = FontWeight.Medium, color = textColor
        )
    }
}

@Composable
private fun DeleteModelDialog(
    modelName: String, onDismiss: () -> Unit, onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(rDP(16.dp)),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = rDP(6.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(rDP(24.dp)),
                verticalArrangement = Arrangement.spacedBy(rDP(16.dp))
            ) {
                Text(
                    text = "Delete Model", style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize),
                        fontWeight = FontWeight.Bold
                    ), color = MaterialTheme.colorScheme.onSurface
                )

                Text(
                    text = "Are you sure you want to delete \"$modelName\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(rDP(8.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss, modifier = Modifier.padding(end = rDP(8.dp))
                    ) {
                        Text(
                            text = "Cancel", style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                            )
                        )
                    }

                    TextButton(
                        onClick = onConfirm
                    ) {
                        Text(
                            text = "Delete", style = MaterialTheme.typography.labelLarge.copy(
                                fontSize = rSp(MaterialTheme.typography.labelLarge.fontSize)
                            ), color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: ImageVector, title: String, subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(rDP(48.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(rDP(12.dp))
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(rDP(80.dp)),
            shadowElevation = rDP(0.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(rDP(40.dp)),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }

        Text(
            text = title, style = MaterialTheme.typography.titleLarge.copy(
                fontSize = rSp(MaterialTheme.typography.titleLarge.fontSize),
                fontWeight = FontWeight.Bold
            ), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = rSp(MaterialTheme.typography.bodyMedium.fontSize)
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
    }
}
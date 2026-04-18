package com.dark.tool_neuron.ui.components.action_window

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.MemoryMetrics
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.TextMetrics
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.CaptionText
import com.dark.tool_neuron.ui.components.SectionHeader
import com.dark.tool_neuron.ui.components.model_list.InstalledModelList
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import com.dark.tool_neuron.viewmodel.ModelLoadState
import com.dark.tool_neuron.viewmodel.PillState

@Composable
fun ActionWindowPill(
    state: PillState,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    val accent = pillAccentColor(state)

    Surface(
        shape = tnShapes.full,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { MutableInteractionSource() },
            onClick = onToggle
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    (fadeIn(Motion.state()) togetherWith fadeOut(Motion.state()))
                },
                label = "pillLeading",
            ) { target ->
                Box(
                    modifier = Modifier.size(dimens.iconSm),
                    contentAlignment = Alignment.Center,
                ) {
                    if (target == PillState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(dimens.iconSm),
                            color = accent,
                            trackColor = accent.copy(alpha = 0.2f),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = pillIcon(target),
                            contentDescription = null,
                            modifier = Modifier.size(dimens.iconSm),
                            tint = accent,
                        )
                    }
                }
            }

            AnimatedContent(
                targetState = state,
                transitionSpec = {
                    fadeIn(Motion.state()) togetherWith fadeOut(Motion.state())
                },
                label = "pillLabel",
            ) { target ->
                Text(
                    text = target.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (expanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun ActionWindowOverlay(
    expanded: Boolean,
    onDismiss: () -> Unit,
    installedModels: List<ModelInfo>,
    modelLoadState: ModelLoadState,
    activeModel: ModelInfo?,
    contextUsage: Float,
    lastTextMetrics: TextMetrics?,
    lastMemoryMetrics: MemoryMetrics?,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: (ModelInfo) -> Unit,
    onStoreClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onLoadDocument: () -> Unit = {},
    documents: List<ChatDocument> = emptyList(),
    onRemoveDocument: (String) -> Unit = {},
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    if (expanded) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onDismiss
                )
        )
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn(Motion.state()) + expandVertically(
                animationSpec = Motion.content(),
                expandFrom = Alignment.Top
            ),
            exit = fadeOut(Motion.state()) + shrinkVertically(
                animationSpec = Motion.content(),
                shrinkTowards = Alignment.Top
            )
        ) {
            Surface(
                shape = tnShapes.xl,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = dimens.cardElevation,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimens.screenPadding)
                    .padding(top = dimens.spacingXs)
            ) {
                ActionWindowContent(
                    installedModels = installedModels,
                    modelLoadState = modelLoadState,
                    activeModel = activeModel,
                    contextUsage = contextUsage,
                    lastTextMetrics = lastTextMetrics,
                    lastMemoryMetrics = lastMemoryMetrics,
                    onLoadModel = onLoadModel,
                    onUnloadModel = onUnloadModel,
                    onStoreClick = onStoreClick,
                    onGuideClick = onGuideClick,
                    onSettingsClick = onSettingsClick,
                    onLoadDocument = onLoadDocument,
                    documents = documents,
                    onRemoveDocument = onRemoveDocument,
                )
            }
        }
    }
}

@Composable
private fun ActionWindowContent(
    installedModels: List<ModelInfo>,
    modelLoadState: ModelLoadState,
    activeModel: ModelInfo?,
    contextUsage: Float,
    lastTextMetrics: TextMetrics?,
    lastMemoryMetrics: MemoryMetrics?,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: (ModelInfo) -> Unit,
    onStoreClick: () -> Unit,
    onGuideClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLoadDocument: () -> Unit,
    documents: List<ChatDocument>,
    onRemoveDocument: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Models", "Stats", "Tools")

    Column(modifier = Modifier.fillMaxWidth()) {
        SecondaryTabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (selectedTab == index) FontWeight.SemiBold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimens.spacingLg)
        ) {
            when (selectedTab) {
                0 -> ModelsTab(
                    installedModels = installedModels,
                    modelLoadState = modelLoadState,
                    onLoadModel = onLoadModel,
                    onUnloadModel = onUnloadModel,
                    onBrowseStore = onStoreClick,
                )
                1 -> StatsTab(
                    activeModel = activeModel,
                    contextUsage = contextUsage,
                    textMetrics = lastTextMetrics,
                    memoryMetrics = lastMemoryMetrics,
                )
                2 -> ToolsTab(
                    onStoreClick = onStoreClick,
                    onGuideClick = onGuideClick,
                    onSettingsClick = onSettingsClick,
                    onLoadDocument = onLoadDocument,
                    documents = documents,
                    onRemoveDocument = onRemoveDocument,
                )
            }
        }
    }
}

@Composable
private fun ModelsTab(
    installedModels: List<ModelInfo>,
    modelLoadState: ModelLoadState,
    onLoadModel: (ModelInfo) -> Unit,
    onUnloadModel: (ModelInfo) -> Unit,
    onBrowseStore: () -> Unit,
) {
    InstalledModelList(
        models = installedModels,
        loadState = modelLoadState,
        onLoad = onLoadModel,
        onUnload = onUnloadModel,
        onBrowseStore = onBrowseStore,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 180.dp, max = 320.dp),
    )
}

@Composable
private fun StatsTab(
    activeModel: ModelInfo?,
    contextUsage: Float,
    textMetrics: TextMetrics?,
    memoryMetrics: MemoryMetrics?,
) {
    val dimens = LocalDimens.current
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()

    val contextPercent = "${(contextUsage * 100).toInt()}%"

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingMd)) {
        SectionHeader(title = "Model")
        StatRow("Name", activeModel?.name ?: "None loaded")
        if (activeModel != null && activeModel.fileSize > 0) {
            StatRow("File size", formatBytes(activeModel.fileSize))
        }

        SectionHeader(title = "Context")
        StatRow("Usage", if (isModelLoaded) contextPercent else "--")
        if (isModelLoaded) {
            LinearProgressIndicator(
                progress = { contextUsage.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                color = if (contextUsage > 0.85f) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            )
        }

        SectionHeader(title = "Performance")
        if (textMetrics != null) {
            StatRow("Generation speed", "%.2f tok/s".format(textMetrics.tokensPerSecond))
            StatRow("Prompt tokens", textMetrics.promptTokens.toString())
            StatRow("Generated tokens", textMetrics.generatedTokens.toString())
            if (textMetrics.timeToFirstTokenMs > 0) {
                StatRow("Time to first token", "${textMetrics.timeToFirstTokenMs} ms")
            }
            if (textMetrics.totalTimeMs > 0) {
                StatRow("Total time", formatDuration(textMetrics.totalTimeMs))
            }
        } else {
            StatRow("Generation speed", if (isModelLoaded) "No data yet" else "--")
        }
        StatRow("Backend", if (isModelLoaded) "CPU" else "--")

        SectionHeader(title = "Memory")
        if (memoryMetrics != null) {
            if (memoryMetrics.modelSizeMB > 0) {
                StatRow("Model size", "%.0f MB".format(memoryMetrics.modelSizeMB))
            }
            if (memoryMetrics.contextSizeMB > 0) {
                StatRow("Context size", "%.0f MB".format(memoryMetrics.contextSizeMB))
            }
            if (memoryMetrics.peakMemoryMB > 0) {
                StatRow("Peak memory", "%.0f MB".format(memoryMetrics.peakMemoryMB))
            }
            if (memoryMetrics.usagePercent > 0) {
                StatRow("Usage", "%.1f%%".format(memoryMetrics.usagePercent))
            }
        } else {
            StatRow("Model size", if (isModelLoaded) "No data yet" else "--")
            StatRow("Context size", if (isModelLoaded) "No data yet" else "--")
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val mb = bytes / (1024.0 * 1024.0)
    if (mb < 1024) return "%.0f MB".format(mb)
    val gb = mb / 1024.0
    return "%.2f GB".format(gb)
}

private fun formatDuration(ms: Long): String =
    if (ms < 1000) "$ms ms" else "%.2f s".format(ms / 1000.0)

@Composable
private fun StatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ToolsTab(
    onStoreClick: () -> Unit,
    onGuideClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onLoadDocument: () -> Unit,
    documents: List<ChatDocument>,
    onRemoveDocument: (String) -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current

    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
        SectionHeader(title = "Actions")
        ActionTextButton(
            onClickListener = onLoadDocument,
            icon = TnIcons.BookOpen,
            text = if (documents.isNotEmpty()) "Load Document (${documents.size})" else "Load Document",
            modifier = Modifier.fillMaxWidth()
        )

        AnimatedVisibility(
            visible = documents.isNotEmpty(),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)) {
                documents.forEach { doc ->
                    Surface(
                        shape = tnShapes.cardSmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(
                                horizontal = dimens.spacingSm,
                                vertical = dimens.spacingXxs
                            ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = TnIcons.BookOpen,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(dimens.spacingXs))
                            Text(
                                text = doc.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            CaptionText(text = formatFileSize(doc.sizeBytes))
                            Spacer(Modifier.width(dimens.spacingXs))
                            Icon(
                                imageVector = TnIcons.X,
                                contentDescription = "Remove",
                                modifier = Modifier
                                    .size(14.dp)
                                    .clickable(
                                        indication = null,
                                        interactionSource = remember { MutableInteractionSource() },
                                        onClick = { onRemoveDocument(doc.id) }
                                    ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        ActionTextButton(
            onClickListener = {},
            icon = TnIcons.Edit,
            text = "System Prompt",
            modifier = Modifier.fillMaxWidth()
        )
        ActionTextButton(
            onClickListener = {},
            icon = TnIcons.Code,
            text = "Chat Template",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(dimens.spacingXs))
        SectionHeader(title = "Quick Links")
        Row(horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm)) {
            ActionTextButton(
                onClickListener = onStoreClick,
                icon = TnIcons.Download,
                text = "Store"
            )
            ActionTextButton(
                onClickListener = onGuideClick,
                icon = TnIcons.Compass,
                text = "Guide"
            )
            ActionTextButton(
                onClickListener = onSettingsClick,
                icon = TnIcons.Settings,
                text = "Settings"
            )
        }
    }
}

@Composable
private fun pillAccentColor(state: PillState): Color = when (state) {
    PillState.Loaded, PillState.Generating, PillState.Thinking, PillState.ToolCalling,
    PillState.Loading, PillState.Image, PillState.Rag -> MaterialTheme.colorScheme.primary
    PillState.Error -> MaterialTheme.colorScheme.error
    PillState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
}

private fun pillIcon(state: PillState): ImageVector = when (state) {
    PillState.Idle -> TnIcons.Leaf
    PillState.Loaded -> TnIcons.Check
    PillState.Generating -> TnIcons.Sparkles
    PillState.Thinking -> TnIcons.Sparkles
    PillState.ToolCalling -> TnIcons.Wrench
    PillState.Image -> TnIcons.Photo
    PillState.Rag -> TnIcons.BookOpen
    PillState.Error -> TnIcons.AlertTriangle
    PillState.Loading -> TnIcons.Leaf
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.0f KB".format(kb)
    val mb = kb / 1024.0
    return "%.1f MB".format(mb)
}

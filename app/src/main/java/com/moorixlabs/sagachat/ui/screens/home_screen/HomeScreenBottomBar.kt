package com.moorixlabs.sagachat.ui.screens.home_screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.ui.components.ActionButton
import com.moorixlabs.sagachat.ui.components.ActionProgressButton
import com.moorixlabs.sagachat.ui.components.CompactProgressBar
import com.moorixlabs.sagachat.ui.components.ContextStatsDialog
import com.moorixlabs.sagachat.ui.components.TnTextField
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.ui.theme.LocalTnShapes
import com.moorixlabs.sagachat.ui.theme.Motion
import com.moorixlabs.sagachat.viewmodel.HomeViewModel

@Composable
fun HomeScreenBottomBar(
    navController: NavHostController,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val dimens = LocalDimens.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    var toolsWindowOpen by remember { mutableStateOf(false) }

    val thinkingEnabled by viewModel.thinkingEnabled.collectAsStateWithLifecycle()
    val supportsThinking by viewModel.supportsThinking.collectAsStateWithLifecycle()
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val installedModels by viewModel.chatModels.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()
    val compactionState by viewModel.compactionState.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()

    val canSend = text.isNotBlank() && !isGenerating && (isModelLoaded || installedModels.isNotEmpty())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = dimens.spacingMd)
            .padding(bottom = dimens.spacingXs),
    ) {
        CompactProgressBar(
            visible = compactionState.active,
            elapsedMs = compactionState.elapsedMs,
            tokensIn = compactionState.tokensIn,
            fraction = compactionState.fraction,
        )

        AnimatedVisibility(
            visible = toolsWindowOpen,
            enter = fadeIn(Motion.state()) + expandVertically(Motion.content()),
            exit = fadeOut(Motion.state()) + shrinkVertically(Motion.content()),
        ) {
            ToolsPickerWindow(
                thinkingEnabled = thinkingEnabled,
                thinkingSupported = supportsThinking,
                webSearchEnabled = false,
                canAttachImage = false,
                canAttachFiles = false,
                canCompact = isModelLoaded
                    && !isGenerating
                    && !compactionState.active
                    && messages.any { it.archivedByCompactId == null }
                    && messages.lastOrNull { it.archivedByCompactId == null }
                        ?.kind != com.moorixlabs.sagachat.model.MessageKind.CompactSummary,
                onToggleThinking = viewModel::toggleThinking,
                onToggleWebSearch = {},
                onAttachImage = {},
                onAttachFiles = {},
                onCompactChat = {
                    toolsWindowOpen = false
                    viewModel.compactConversation()
                },
            )
        }

        InputBar(
            text = text,
            onTextChange = { text = it },
            canSend = canSend,
            isGenerating = isGenerating,
            isModelLoaded = isModelLoaded,
            contextUsage = contextUsage,
            toolsOpen = toolsWindowOpen,
            onToggleTools = { toolsWindowOpen = !toolsWindowOpen },
            onSend = {
                if (canSend) {
                    focusManager.clearFocus()
                    val toSend = text
                    text = ""
                    viewModel.sendMessage(toSend)
                }
            },
            onStop = viewModel::stopGeneration,
            onContextClick = viewModel::openContextStats,
        )
    }

    val contextStatsReport by viewModel.contextStatsReport.collectAsStateWithLifecycle()
    contextStatsReport?.let { report ->
        ContextStatsDialog(report = report, onDismiss = viewModel::dismissContextStats)
    }
}

@Composable
private fun InputBar(
    text: String,
    onTextChange: (String) -> Unit,
    canSend: Boolean,
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    contextUsage: Float,
    toolsOpen: Boolean,
    onToggleTools: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onContextClick: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val contextText = if (isModelLoaded) "Context ${(contextUsage * 100).toInt()}%" else "Context --"
    val contextProgress = if (isModelLoaded) contextUsage else 0f

    Surface(
        shape = tnShapes.xl,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.09f)
            .compositeOver(MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            TnTextField(
                value = text,
                onValueChange = onTextChange,
                placeholder = "Send a message...",
                modifier = Modifier.fillMaxWidth(),
                maxLines = 5,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimens.spacingSm,
                        end = dimens.spacingSm,
                        bottom = dimens.spacingSm,
                    ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
            ) {
                ToolsToggleButton(open = toolsOpen, onClick = onToggleTools)
                Spacer(Modifier.weight(1f))
                ContextIndicator(
                    text = contextText,
                    progress = contextProgress,
                    isActive = isModelLoaded,
                    onClick = { if (isModelLoaded) onContextClick() },
                )
                Spacer(Modifier.width(dimens.spacingXs))
                SendOrStopButton(
                    canSend = canSend,
                    isGenerating = isGenerating,
                    onSend = onSend,
                    onStop = onStop,
                )
            }
        }
    }
}

@Composable
private fun ToolsToggleButton(open: Boolean, onClick: () -> Unit) {
    val colors = if (open) IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
    ) else IconButtonDefaults.filledIconButtonColors(
        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
        contentColor = MaterialTheme.colorScheme.primary,
    )
    ActionButton(
        onClickListener = onClick,
        icon = TnIcons.Plus,
        contentDescription = if (open) "Close tools" else "Open tools",
        colors = colors,
    )
}

@Composable
private fun SendOrStopButton(
    canSend: Boolean,
    isGenerating: Boolean,
    onSend: () -> Unit,
    onStop: () -> Unit,
) {
    val tnShapes = LocalTnShapes.current
    if (isGenerating) {
        ActionProgressButton(
            onClickListener = onStop,
            icon = TnIcons.PlayerStop,
            contentDescription = "Stop generation",
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
        )
    } else {
        ActionButton(
            onClickListener = onSend,
            icon = TnIcons.Send,
            contentDescription = "Send",
            enabled = canSend,
            shape = tnShapes.full,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (canSend)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.primary.copy(0.15f),
                contentColor = if (canSend)
                    MaterialTheme.colorScheme.onPrimary
                else
                    MaterialTheme.colorScheme.primary.copy(0.4f),
            ),
        )
    }
}

@Composable
fun ContextIndicator(
    text: String,
    progress: Float,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    val dimens = com.moorixlabs.sagachat.ui.theme.LocalDimens.current
    val tnShapes = com.moorixlabs.sagachat.ui.theme.LocalTnShapes.current
    val color = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary else androidx.compose.material3.MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    androidx.compose.material3.Surface(
        shape = tnShapes.full,
        color = if (isActive) androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.08f) else androidx.compose.ui.graphics.Color.Transparent,
        modifier = Modifier.clickable(
            indication = null,
            interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
            onClick = onClick
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs)
        ) {
            androidx.compose.material3.Icon(
                imageVector = com.moorixlabs.sagachat.ui.icons.TnIcons.Database,
                contentDescription = null,
                modifier = Modifier.size(dimens.iconSm),
                tint = color
            )
            androidx.compose.material3.Text(
                text = text,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                color = color
            )
        }
    }
}

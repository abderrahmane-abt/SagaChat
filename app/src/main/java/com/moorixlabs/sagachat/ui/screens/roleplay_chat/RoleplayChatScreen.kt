package com.moorixlabs.sagachat.ui.screens.roleplay_chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import android.widget.Toast
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moorixlabs.sagachat.model.MemoryState
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.ui.components.TnTextField
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.ui.theme.LocalTnShapes
import com.moorixlabs.sagachat.viewmodel.HomeViewModel
import com.moorixlabs.sagachat.viewmodel.RoleplayChatViewModel
import com.moorixlabs.sagachat.util.shareJsonFile
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoleplayChatScreen(
    characterId: String,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    homeViewModel: HomeViewModel = hiltViewModel(),
    rpViewModel: RoleplayChatViewModel = hiltViewModel(),
) {
    val character by rpViewModel.character.collectAsStateWithLifecycle()
    val memoryState by rpViewModel.memoryState.collectAsStateWithLifecycle()
    val showMemoryPanel by rpViewModel.showMemoryPanel.collectAsStateWithLifecycle()
    val initError by rpViewModel.initError.collectAsStateWithLifecycle()

    val messages by homeViewModel.messages.collectAsStateWithLifecycle()
    val streamingFragment by homeViewModel.streamingFragment.collectAsStateWithLifecycle()
    val isGenerating by homeViewModel.isGenerating.collectAsStateWithLifecycle()
    val isModelLoaded by InferenceClient.isModelLoaded.collectAsStateWithLifecycle()
    val installedModels by homeViewModel.chatModels.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val clipboardManager: ClipboardManager = LocalClipboardManager.current

    val charName = character?.chatName ?: "…"

    // ── Init ──────────────────────────────────────────────────
    LaunchedEffect(characterId) {
        rpViewModel.init(characterId)
    }

    LaunchedEffect(character?.id) {
        val c = character ?: return@LaunchedEffect
        val chatId = rpViewModel.resolveOrCreateChatId()
        homeViewModel.selectChat(chatId)
        rpViewModel.ensureInitialMessage(chatId, messages)
    }

    LaunchedEffect(isGenerating) {
        if (!isGenerating && messages.isNotEmpty()) {
            rpViewModel.onTurnCompleted(messages)
        }
    }

    LaunchedEffect(initError) {
        if (initError != null) onBack()
    }

    // ── Layout ────────────────────────────────────────────────
    Box(modifier = Modifier.fillMaxSize()) {

        Column(modifier = Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────
            RpTopBar(
                charName = charName,
                isGenerating = isGenerating,
                hasMemory = memoryState.summary.isNotBlank(),
                onBack = onBack,
                onMemory = rpViewModel::toggleMemoryPanel,
                onCopyJson = {
                    val json = rpViewModel.exportSessionJson(messages)
                    if (json != null) {
                        clipboardManager.setText(AnnotatedString(json))
                        Toast.makeText(context, "Chat JSON copied", Toast.LENGTH_SHORT).show()
                    }
                },
                onExportFile = {
                    val json = rpViewModel.exportSessionJson(messages)
                    val fileName = rpViewModel.exportSessionFileName()
                    if (json != null) {
                        shareJsonFile(context, json, fileName)
                    }
                }
            )

            // ── Message list ──────────────────────────────────
            RpMessageList(
                modifier = Modifier.weight(1f),
                messages = messages,
                streamingContent = streamingFragment?.content.orEmpty(),
                isGenerating = isGenerating,
                charName = charName,
            )

            // ── Input bar ─────────────────────────────────────
            RpInputBar(
                isGenerating = isGenerating,
                isModelLoaded = isModelLoaded || installedModels.isNotEmpty(),
                onSend = homeViewModel::sendMessage,
                onStop = homeViewModel::stopGeneration,
            )
        }

        // ── Memory panel — slide in from top ──────────────────
        AnimatedVisibility(
            visible = showMemoryPanel,
            enter = fadeIn(tween(200)) + slideInVertically(tween(300)) { -it / 2 },
            exit = fadeOut(tween(200)) + slideOutVertically(tween(250)) { -it / 2 },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            RpMemoryPanel(
                memoryState = memoryState,
                charName = charName,
                onDismiss = rpViewModel::dismissMemoryPanel,
                onReset = {
                    rpViewModel.resetMemory()
                    rpViewModel.dismissMemoryPanel()
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Top bar
// ─────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RpTopBar(
    charName: String,
    isGenerating: Boolean,
    hasMemory: Boolean,
    onBack: () -> Unit,
    onMemory: () -> Unit,
    onCopyJson: () -> Unit,
    onExportFile: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Avatar circle
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = TnIcons.HatGlasses,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column {
                    Text(
                        text = charName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (isGenerating) "Typing…" else "Roleplay",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isGenerating)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(TnIcons.ArrowLeft, contentDescription = "Back")
            }
        },
        actions = {
            IconButton(onClick = onMemory) {
                Icon(
                    imageVector = TnIcons.Database,
                    contentDescription = "Memory",
                    tint = if (hasMemory)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { expanded = true }) {
                    Icon(TnIcons.More, contentDescription = "More")
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Copy JSON") },
                        onClick = {
                            expanded = false
                            onCopyJson()
                        },
                        leadingIcon = { Icon(TnIcons.Copy, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                    DropdownMenuItem(
                        text = { Text("Export File") },
                        onClick = {
                            expanded = false
                            onExportFile()
                        },
                        leadingIcon = { Icon(TnIcons.Share, contentDescription = null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
        },
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}

// ─────────────────────────────────────────────────────────────
// Message list
// ─────────────────────────────────────────────────────────────

@Composable
private fun RpMessageList(
    messages: List<com.moorixlabs.sagachat.model.ChatMessage>,
    streamingContent: String,
    isGenerating: Boolean,
    charName: String,
    modifier: Modifier = Modifier,
) {
    val dimens = LocalDimens.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val totalItems = messages.size + if (isGenerating) 1 else 0

    // Scroll to bottom when new content arrives
    LaunchedEffect(messages.size, streamingContent.length) {
        if (totalItems > 0) {
            scope.launch {
                listState.scrollToItem(totalItems - 1, scrollOffset = Int.MAX_VALUE)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(
            horizontal = dimens.spacingMd,
            vertical = dimens.spacingMd,
        ),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingMd),
    ) {
        items(items = messages, key = { it.id }) { message ->
            RpMessageBubble(message = message, charName = charName)
        }

        if (isGenerating) {
            item(key = "__streaming__") {
                RpStreamingBubble(content = streamingContent, charName = charName)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Input bar
// ─────────────────────────────────────────────────────────────

@Composable
private fun RpInputBar(
    isGenerating: Boolean,
    isModelLoaded: Boolean,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    val focusManager = LocalFocusManager.current
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank() && !isGenerating && isModelLoaded

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        ) {
            // Text field
            Surface(
                shape = tnShapes.xl,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    .compositeOver(MaterialTheme.colorScheme.surface),
                modifier = Modifier.weight(1f),
            ) {
                TnTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = "Say something…",
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 5,
                )
            }

            // Send / Stop button
            if (isGenerating) {
                FilledIconButton(
                    onClick = onStop,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                ) {
                    Icon(TnIcons.PlayerStop, contentDescription = "Stop")
                }
            } else {
                FilledIconButton(
                    onClick = {
                        val toSend = text.trim()
                        if (toSend.isNotBlank()) {
                            focusManager.clearFocus()
                            text = ""
                            onSend(toSend)
                        }
                    },
                    enabled = canSend,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (canSend)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        contentColor = if (canSend)
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    ),
                ) {
                    Icon(TnIcons.Send, contentDescription = "Send")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Memory panel — slides in from top as an overlay card
// ─────────────────────────────────────────────────────────────

@Composable
private fun RpMemoryPanel(
    memoryState: MemoryState,
    charName: String,
    onDismiss: () -> Unit,
    onReset: () -> Unit,
) {
    val dimens = LocalDimens.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimens.spacingMd),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = TnIcons.Database,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = "$charName — Memory",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(TnIcons.X, contentDescription = "Close", modifier = Modifier.size(16.dp))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Content
            if (memoryState.summary.isBlank()) {
                Text(
                    text = "No memory yet. A summary will appear here after a few exchanges.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "Summary",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = memoryState.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (memoryState.summary.isNotBlank()) {
                    TextButton(
                        onClick = onReset,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) { Text("Reset") }
                }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}

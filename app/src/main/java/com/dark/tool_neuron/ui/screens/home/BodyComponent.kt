package com.dark.tool_neuron.ui.screens.home

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.rememberAsyncImagePainter
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.components.MarkdownText
import com.dark.tool_neuron.ui.theme.rDP
import com.dark.tool_neuron.ui.theme.rSp
import com.dark.tool_neuron.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.tool_neuron.viewModel.chatViewModel.TTSViewModel
import com.dark.tool_neuron.viewModel.home_screen.HomeScreenViewModel
import com.mp.user_data.models.ChatMessage
import com.mp.user_data.models.ChatMessageContent
import com.mp.user_data.models.ChatMessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

// Stable display state to prevent unnecessary recompositions
private data class MessageDisplayState(
    val isUser: Boolean,
    val isGenerating: Boolean,
    val isEmpty: Boolean,
    val contentType: ContentType
)

private enum class ContentType {
    TEXT, IMAGE, EMPTY
}

@Composable
fun ModernChatBubble(
    message: ChatMessage,
    homeScreenViewModel: HomeScreenViewModel,
    isCurrentlyGenerating: Boolean = false
) {
    // Compute stable display state
    val displayState = remember(message.id, message.chatMessageType, message.chatMessageContent, isCurrentlyGenerating) {
        val contentType = when (message.chatMessageContent) {
            is ChatMessageContent.TextMessage -> ContentType.TEXT
            is ChatMessageContent.ImageMessage -> ContentType.IMAGE
            else -> ContentType.EMPTY
        }

        MessageDisplayState(
            isUser = message.chatMessageType == ChatMessageType.USER,
            isGenerating = isCurrentlyGenerating,
            isEmpty = message.input.isEmpty() && message.chatMessageContent is ChatMessageContent.None,
            contentType = contentType
        )
    }

    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn() + scaleIn(
            initialScale = 0.8f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        exit = fadeOut() + scaleOut()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = if (displayState.isUser) Arrangement.End else Arrangement.Start
        ) {
            when {
                displayState.isUser -> UserMessageBubble(
                    message = message,
                    onDelete = { homeScreenViewModel.deleteMessage(it) }
                )
                displayState.isEmpty && displayState.isGenerating -> GeneratingPlaceholder()
                else -> AssistantMessageBubble(
                    message = message,
                    homeScreenViewModel = homeScreenViewModel,
                    isGenerating = displayState.isGenerating,
                    contentType = displayState.contentType
                )
            }
        }
    }
}

@Composable
private fun GeneratingPlaceholder() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(8.dp)),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val infiniteTransition = rememberInfiniteTransition(label = "generating")
        val alpha by infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(rDP(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                modifier = Modifier.size(rDP(16.dp))
            )
            Text(
                text = "Generating response...",
                color = MaterialTheme.colorScheme.primary.copy(alpha = alpha),
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic
            )
        }
    }
}

@Composable
private fun UserMessageBubble(
    message: ChatMessage,
    onDelete: (String) -> Unit
) {
    val radius = with(LocalDensity.current) { rDP(16.dp) }
    val corner = RoundedCornerShape(
        topStart = radius,
        topEnd = radius,
        bottomStart = radius,
        bottomEnd = 4.dp
    )
    val clipboardManager = LocalClipboard.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.widthIn(max = rDP(280.dp)),
        horizontalAlignment = Alignment.End
    ) {
        // Message content
        Box(
            modifier = Modifier
                .clip(corner)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    shape = corner
                )
                .padding(horizontal = rDP(16.dp), vertical = rDP(12.dp))
        ) {
            when (val content = message.chatMessageContent) {
                is ChatMessageContent.TextMessage -> {
                    Text(
                        text = content.text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is ChatMessageContent.ImageMessage -> {
                    UserImageContent(imagePath = content.imagePath)
                }
                else -> {
                    Text(
                        text = message.input,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(rDP(8.dp)))

        // Action buttons
        MessageActionRow(
            onCopy = {
                scope.launch {
                    val textToCopy = when (val content = message.chatMessageContent) {
                        is ChatMessageContent.TextMessage -> content.text
                        else -> message.input
                    }
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("message", textToCopy))
                    )
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            },
            onShare = {
                val textToShare = when (val content = message.chatMessageContent) {
                    is ChatMessageContent.TextMessage -> content.text
                    else -> message.input
                }
                shareText(context, textToShare)
            },
            onDelete = { onDelete(message.id) }
        )
    }
}

@Composable
private fun UserImageContent(imagePath: String) {
    val file = File(imagePath)
    if (file.exists()) {
        Column(verticalArrangement = Arrangement.spacedBy(rDP(8.dp))) {
            Image(
                painter = rememberAsyncImagePainter(file),
                contentDescription = "User image",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = rDP(200.dp))
                    .clip(RoundedCornerShape(rDP(8.dp))),
                contentScale = ContentScale.Fit
            )
        }
    }
}

@Composable
private fun AssistantMessageBubble(
    message: ChatMessage,
    isGenerating: Boolean,
    contentType: ContentType,
    homeScreenViewModel: HomeScreenViewModel
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = rDP(4.dp))
    ) {
        // Message content based on type
        when (contentType) {
            ContentType.TEXT -> {
                val content = message.chatMessageContent as? ChatMessageContent.TextMessage
                val text = content?.text ?: ""

                if (isGenerating && text.isEmpty()) {
                    GeneratingPlaceholder()
                } else if (isGenerating) {
                    // Show plain text during streaming
                    Text(
                        text = text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // Show markdown after generation complete
                    MarkdownText(
                        text = text,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            ContentType.IMAGE -> {
                AssistantImageContent(
                    message = message,
                    isGenerating = isGenerating
                )
            }
            ContentType.EMPTY -> {
                Text(
                    text = "No content available",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic
                )
            }
        }

        Spacer(Modifier.height(rDP(8.dp)))

        // Action buttons (only for text content)
        if (contentType == ContentType.TEXT && !isGenerating) {
            AssistantMessageActions(
                message = message,
                scope = scope,
                context = context,
                onDeleteClick = { homeScreenViewModel.deleteMessage(message.id) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AssistantImageContent(
    message: ChatMessage,
    isGenerating: Boolean
) {
    val content = message.chatMessageContent as? ChatMessageContent.ImageMessage ?: return

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDP(12.dp)),
        elevation = CardDefaults.cardElevation(rDP(2.dp))
    ) {
        Column(
            modifier = Modifier.padding(rDP(12.dp)),
            verticalArrangement = Arrangement.spacedBy(rDP(8.dp))
        ) {
            // Image display
            val file = File(content.imagePath)
            if (file.exists()) {
                Image(
                    painter = rememberAsyncImagePainter(file),
                    contentDescription = "Generated image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = rDP(300.dp))
                        .clip(RoundedCornerShape(rDP(8.dp))),
                    contentScale = ContentScale.Fit
                )
            }

            // Progress indicator for generating images
            if (isGenerating && content.totalSteps > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(rDP(4.dp))) {
                    LinearWavyProgressIndicator(
                        progress = { content.currentStep.toFloat() / content.totalSteps.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(rDP(6.dp)),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Step ${content.currentStep} of ${content.totalSteps}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageActionRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val iconSize = rDP(16.dp)

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            painter = painterResource(R.drawable.copy),
            contentDescription = "Copy",
            onClick = onCopy,
            iconSize = iconSize
        )
        ActionIcon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = onShare,
            iconSize = iconSize
        )
        ActionIcon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            onClick = onDelete,
            iconSize = iconSize
        )
    }
}

@Composable
private fun AssistantMessageActions(
    message: ChatMessage,
    scope: CoroutineScope,
    context: Context,
    onDeleteClick: () -> Unit,
) {
    val clipboardManager = LocalClipboard.current
    val iconSize = rDP(16.dp)

    val textContent = (message.chatMessageContent as? ChatMessageContent.TextMessage)?.text ?: ""

    Row(
        horizontalArrangement = Arrangement.spacedBy(rDP(12.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionIcon(
            painter = painterResource(R.drawable.copy),
            contentDescription = "Copy",
            onClick = {
                scope.launch {
                    clipboardManager.setClipEntry(
                        ClipEntry(ClipData.newPlainText("message", textContent))
                    )
                    Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                }
            },
            iconSize = iconSize
        )

        ActionIcon(
            imageVector = Icons.Rounded.Share,
            contentDescription = "Share",
            onClick = { shareText(context, textContent) },
            iconSize = iconSize
        )

        ActionIcon(
            imageVector = Icons.Rounded.DeleteOutline,
            contentDescription = "Delete",
            onClick = onDeleteClick,
            iconSize = iconSize
        )
    }
}

@Composable
private fun ActionIcon(
    imageVector: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp,
    enabled: Boolean = true
) {
    Icon(
        imageVector = imageVector,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.7f else 0.3f),
        modifier = Modifier
            .size(iconSize)
            .clickable(enabled = enabled) { onClick() }
    )
}

@Composable
private fun ActionIcon(
    painter: Painter,
    contentDescription: String,
    onClick: () -> Unit,
    iconSize: Dp,
    enabled: Boolean = true
) {
    Icon(
        painter = painter,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.7f else 0.3f),
        modifier = Modifier
            .size(iconSize)
            .clickable(enabled = enabled) { onClick() }
    )
}

@Composable
private fun TTSActionButton(
    isPlaying: Boolean,
    progress: Float,
    isInitialized: Boolean,
    iconSize: Dp,
    onClick: () -> Unit
) {
    Box(contentAlignment = Alignment.Center) {
        if (isPlaying && progress > 0f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(iconSize + 4.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                strokeWidth = 2.dp,
                trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor
            )
        }

        Icon(
            painter = painterResource(if (isPlaying) R.drawable.stop else R.drawable.speaker),
            contentDescription = if (isPlaying) "Stop audio" else "Play audio",
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (isInitialized) 0.7f else 0.3f),
            modifier = Modifier
                .size(iconSize)
                .clickable(enabled = isInitialized) { onClick() }
        )
    }
}

// Helper function
private fun shareText(context: Context, text: String) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, text)
        type = "text/plain"
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share message"))
}
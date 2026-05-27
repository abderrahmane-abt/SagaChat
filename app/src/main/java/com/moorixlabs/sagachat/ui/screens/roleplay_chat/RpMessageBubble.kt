package com.moorixlabs.sagachat.ui.screens.roleplay_chat

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moorixlabs.sagachat.model.ChatMessage
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.ui.theme.LocalTnShapes

private const val ROLE_USER = "user"

/**
 * Roleplay-specific message bubble.
 *
 * - User messages  → right-aligned pill bubble (primaryContainer).
 * - Assistant messages → left-aligned prose with inline styled spans:
 *     *actions*  → italic + muted colour
 *     "dialogue" → medium-weight + slightly brighter colour
 *     bare prose → standard body colour
 */
@Composable
fun RpMessageBubble(message: ChatMessage, charName: String) {
    if (message.role == ROLE_USER) {
        RpUserBubble(message)
    } else {
        RpAssistantBubble(message = message, charName = charName)
    }
}

// ─────────────────────────────────────────────────────────────
// User bubble
// ─────────────────────────────────────────────────────────────

@Composable
private fun RpUserBubble(message: ChatMessage) {
    val dimens = LocalDimens.current
    val tnShapes = LocalTnShapes.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
    ) {
        Surface(
            shape = tnShapes.lg,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.widthIn(max = 280.dp),
        ) {
            val actionColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            val dialogueColor = MaterialTheme.colorScheme.onPrimaryContainer
            val proseColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
            
            SelectionContainer {
                Text(
                    text = buildRpAnnotatedString(message.content, actionColor, dialogueColor, proseColor),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingMd,
                        vertical = dimens.spacingSm,
                    ),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Assistant bubble — roleplay styled text
// ─────────────────────────────────────────────────────────────

@Composable
private fun RpAssistantBubble(message: ChatMessage, charName: String) {
    val dimens = LocalDimens.current
    val actionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
    val dialogueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.93f)
    val proseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 40.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = charName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp),
        )
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            SelectionContainer {
                Text(
                    text = buildRpAnnotatedString(message.content, actionColor, dialogueColor, proseColor),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Streaming bubble shown while model generates
// ─────────────────────────────────────────────────────────────

@Composable
fun RpStreamingBubble(content: String, charName: String) {
    val dimens = LocalDimens.current
    val actionColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
    val dialogueColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.93f)
    val proseColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 40.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = charName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 2.dp),
        )
        Surface(
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            if (content.isBlank()) {
                RpTypingDots(
                    modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                )
            } else {
                Text(
                    text = buildRpAnnotatedString(content, actionColor, dialogueColor, proseColor),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = dimens.spacingMd, vertical = dimens.spacingSm),
                    lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.15f,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Animated three-dot typing indicator
// ─────────────────────────────────────────────────────────────

@Composable
fun RpTypingDots(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rp_typing")
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(0, 160, 320).forEach { delayMs ->
            val alpha by transition.animateFloat(
                initialValue = 0.25f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 600, delayMillis = delayMs),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot_$delayMs",
            )
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = alpha), CircleShape),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Inline text parser — *actions*, "dialogue", prose
// ─────────────────────────────────────────────────────────────

private fun buildRpAnnotatedString(
    raw: String,
    actionColor: Color,
    dialogueColor: Color,
    proseColor: Color,
) = buildAnnotatedString {
    val text = raw.trim()
    var i = 0
    while (i < text.length) {
        when (text[i]) {
            '*' -> {
                val end = text.indexOf('*', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic, color = actionColor))
                    append(text.substring(i + 1, end)) // exclude the * markers
                    pop()
                    i = end + 1
                } else {
                    pushStyle(SpanStyle(color = proseColor)); append(text[i]); pop(); i++
                }
            }
            '"' -> {
                val end = text.indexOf('"', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(color = dialogueColor, fontWeight = FontWeight.Medium))
                    append(text.substring(i, end + 1))
                    pop()
                    i = end + 1
                } else {
                    pushStyle(SpanStyle(color = proseColor)); append(text[i]); pop(); i++
                }
            }
            '\u201C' -> { // left curly quote "
                val end = text.indexOf('\u201D', i + 1)
                if (end != -1) {
                    pushStyle(SpanStyle(color = dialogueColor, fontWeight = FontWeight.Medium))
                    append(text.substring(i, end + 1))
                    pop()
                    i = end + 1
                } else {
                    pushStyle(SpanStyle(color = proseColor)); append(text[i]); pop(); i++
                }
            }
            else -> {
                pushStyle(SpanStyle(color = proseColor))
                append(text[i])
                pop()
                i++
            }
        }
    }
}

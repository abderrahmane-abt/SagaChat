package com.mp.n_apps.renderer.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp.n_apps.expression.Evaluator
import com.mp.n_apps.renderer.ComponentRegistry
import com.mp.n_apps.renderer.IconResolver

fun registerDisplayComponents(registry: ComponentRegistry) {

    // ==================== text ====================
    registry.register("text") { spec, state, resolver, _, _, _ ->
        val content = spec.content?.let { resolver.resolveString(it, state) } ?: return@register

        val (textStyle, fontWeight) = when (spec.style) {
            "h1" -> MaterialTheme.typography.titleLarge to FontWeight.Bold
            "h2" -> MaterialTheme.typography.titleMedium to FontWeight.SemiBold
            "h3" -> MaterialTheme.typography.titleSmall to FontWeight.SemiBold
            "body" -> MaterialTheme.typography.bodyMedium to FontWeight.Normal
            "caption" -> MaterialTheme.typography.labelSmall to FontWeight.Normal
            "label" -> MaterialTheme.typography.labelLarge to FontWeight.SemiBold
            "overline" -> MaterialTheme.typography.labelSmall to FontWeight.Medium
            else -> MaterialTheme.typography.bodyMedium to FontWeight.Normal
        }

        val color = when (spec.style) {
            "caption", "overline" -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            "label" -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }

        Text(
            text = content,
            style = textStyle,
            fontWeight = fontWeight,
            color = color,
            maxLines = spec.maxLines ?: Int.MAX_VALUE,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )
    }

    // ==================== markdown ====================
    // Simplified: renders as styled text (full markdown parsing deferred to V2)
    registry.register("markdown") { spec, state, resolver, _, _, _ ->
        val content = spec.content?.let { resolver.resolveString(it, state) } ?: return@register

        Text(
            text = content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        )
    }

    // ==================== code_block ====================
    registry.register("code_block") { spec, state, resolver, _, _, _ ->
        val content = spec.content?.let { resolver.resolveString(it, state) } ?: return@register

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Box(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(10.dp)
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    // ==================== divider ====================
    registry.register("divider") { _, _, _, _, _, _ ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )
    }

    // ==================== spacer ====================
    registry.register("spacer") { spec, _, _, _, _, _ ->
        val h = spec.height ?: 8
        Spacer(modifier = Modifier.height(h.dp))
    }

    // ==================== card ====================
    // Matches StandardCard: surfaceVariant(0.3f), 6dp corners, compact padding
    registry.register("card") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val title = spec.title?.let { resolver.resolveString(it, state) }
        val subtitle = spec.subtitle?.let { resolver.resolveString(it, state) }
        val headerIconName = spec.headerIcon

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            shape = RoundedCornerShape(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (title != null || headerIconName != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (headerIconName != null) {
                            Icon(
                                imageVector = IconResolver.resolveIcon(headerIconName),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            if (title != null) {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                // Render children inside card
                spec.children?.forEach { childId ->
                    renderChild(childId)
                }
            }
        }
    }

    // ==================== progress ====================
    registry.register("progress") { spec, state, resolver, _, _, _ ->
        val indeterminate = spec.indeterminate ?: false
        val progress = spec.progress?.let {
            val resolved = resolver.resolveAny(it, state)
            Evaluator.toNum(resolved).toFloat()
        } ?: 0f
        val maxProgress = spec.maxProgress?.let {
            val resolved = resolver.resolveAny(it, state)
            Evaluator.toNum(resolved).toFloat()
        } ?: 100f

        val fraction = if (maxProgress > 0f) (progress / maxProgress).coerceIn(0f, 1f) else 0f

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.Center
        ) {
            when (spec.style) {
                "circular" -> {
                    if (indeterminate) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    } else {
                        CircularProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        )
                    }
                }
                else -> {
                    if (indeterminate) {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        )
                    }
                }
            }
        }
    }

    // ==================== icon ====================
    registry.register("icon") { spec, _, _, _, _, _ ->
        val iconName = spec.icon ?: spec.content
        val size = spec.height ?: 24

        Box(
            modifier = Modifier
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = IconResolver.resolveIcon(iconName),
                contentDescription = null,
                modifier = Modifier.size(size.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

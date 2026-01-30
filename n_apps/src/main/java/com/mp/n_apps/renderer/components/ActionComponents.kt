package com.mp.n_apps.renderer.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mp.n_apps.renderer.ComponentRegistry
import com.mp.n_apps.renderer.IconResolver

fun registerActionComponents(registry: ComponentRegistry) {

    // ==================== button ====================
    // Matches ActionTextButton style: 30dp height, 6dp corners, primary.copy(0.06f)
    registry.register("button") { spec, state, resolver, _, onAction, _ ->
        val text = spec.text?.let { resolver.resolveString(it, state) } ?: ""
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false
        val iconName = spec.icon
        val actionId = spec.actionId
        val onClick = { actionId?.let { onAction(it) } }

        when (spec.style) {
            "primary" -> {
                Button(
                    onClick = { onClick() },
                    enabled = !disabled,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (iconName != null) {
                        Icon(
                            imageVector = IconResolver.resolveIcon(iconName),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            "outlined" -> {
                OutlinedButton(
                    onClick = { onClick() },
                    enabled = !disabled,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (iconName != null) {
                        Icon(
                            imageVector = IconResolver.resolveIcon(iconName),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            "text" -> {
                TextButton(
                    onClick = { onClick() },
                    enabled = !disabled,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    if (iconName != null) {
                        Icon(
                            imageVector = IconResolver.resolveIcon(iconName),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            else -> {
                // Default: tonal style matching ActionTextButton
                FilledTonalButton(
                    onClick = { onClick() },
                    enabled = !disabled,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .height(30.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    if (iconName != null) {
                        Icon(
                            imageVector = IconResolver.resolveIcon(iconName),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }

    // ==================== icon_button ====================
    // Matches ActionButton: 30dp square, primary.copy(0.06f) fill, 6dp corners
    registry.register("icon_button") { spec, state, resolver, _, onAction, _ ->
        val iconName = spec.icon ?: "info"
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false
        val actionId = spec.actionId

        Surface(
            modifier = Modifier
                .size(30.dp)
                .clickable(enabled = !disabled) { actionId?.let { onAction(it) } },
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            shape = RoundedCornerShape(6.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = IconResolver.resolveIcon(iconName),
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        .padding(6.dp),
                    tint = if (!disabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }

    // ==================== fab ====================
    registry.register("fab") { spec, state, resolver, _, onAction, _ ->
        val text = spec.text?.let { resolver.resolveString(it, state) }
        val iconName = spec.icon
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false
        val actionId = spec.actionId

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            contentAlignment = Alignment.CenterEnd
        ) {
            if (text != null) {
                FloatingActionButton(
                    onClick = { if (!disabled) actionId?.let { onAction(it) } },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    ) {
                        if (iconName != null) {
                            Icon(
                                imageVector = IconResolver.resolveIcon(iconName),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                        }
                        Text(
                            text = text,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else {
                FloatingActionButton(
                    onClick = { if (!disabled) actionId?.let { onAction(it) } },
                    shape = RoundedCornerShape(8.dp),
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                ) {
                    Icon(
                        imageVector = IconResolver.resolveIcon(iconName),
                        contentDescription = null,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }

    // ==================== submit_group ====================
    // Row of buttons, like MultiActionButton but action-driven
    registry.register("submit_group") { spec, state, resolver, onStateChange, onAction, renderChild ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            spec.children?.forEach { childId ->
                renderChild(childId)
            }
        }
    }
}

package com.mp.n_apps.renderer.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mp.n_apps.expression.Evaluator
import com.mp.n_apps.renderer.ComponentRegistry
import com.mp.n_apps.renderer.IconResolver

fun registerInputComponents(registry: ComponentRegistry) {

    // ==================== text_input ====================
    registry.register("text_input") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val currentValue = Evaluator.toStr(state[key]).let { if (it == "null") "" else it }
        val label = spec.label
        val placeholder = spec.placeholder
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            ) {
                BasicTextField(
                    value = currentValue,
                    onValueChange = { if (!disabled) onStateChange(key, it) },
                    enabled = !disabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (currentValue.isEmpty() && placeholder != null) {
                                Text(
                                    text = resolver.resolveString(placeholder, state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }

    // ==================== text_area ====================
    registry.register("text_area") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val currentValue = Evaluator.toStr(state[key]).let { if (it == "null") "" else it }
        val label = spec.label
        val placeholder = spec.placeholder
        val maxLines = spec.maxLines ?: 5
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            ) {
                BasicTextField(
                    value = currentValue,
                    onValueChange = { if (!disabled) onStateChange(key, it) },
                    enabled = !disabled,
                    singleLine = false,
                    maxLines = maxLines,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((maxLines * 20).dp)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    decorationBox = { innerTextField ->
                        Box {
                            if (currentValue.isEmpty() && placeholder != null) {
                                Text(
                                    text = resolver.resolveString(placeholder, state),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
        }
    }

    // ==================== number_input ====================
    registry.register("number_input") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val currentValue = state[key]
        val label = spec.label
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false
        val displayText = when (currentValue) {
            is Double -> if (currentValue == currentValue.toLong().toDouble()) currentValue.toLong().toString() else currentValue.toString()
            is Number -> currentValue.toString()
            else -> Evaluator.toStr(currentValue).let { if (it == "null") "" else it }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(6.dp)
            ) {
                BasicTextField(
                    value = displayText,
                    onValueChange = { text ->
                        if (!disabled) {
                            val num = text.toDoubleOrNull()
                            if (num != null) onStateChange(key, num)
                            else if (text.isEmpty() || text == "-") onStateChange(key, text)
                        }
                    },
                    enabled = !disabled,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.bodyMedium.fontSize
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }

    // ==================== slider ====================
    registry.register("slider") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val currentValue = Evaluator.toNum(state[key]).toFloat()
        val label = spec.label
        val min = (spec.min ?: 0.0).toFloat()
        val max = (spec.max ?: 100.0).toFloat()
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            if (label != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = resolver.resolveString(label, state),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = Evaluator.toStr(currentValue.toDouble()),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Slider(
                value = currentValue,
                onValueChange = { if (!disabled) onStateChange(key, it.toDouble()) },
                enabled = !disabled,
                valueRange = min..max,
                steps = spec.step?.let { ((max - min) / it.toFloat()).toInt() - 1 } ?: 0,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    // ==================== dropdown ====================
    registry.register("dropdown") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val currentValue = Evaluator.toStr(state[key])
        val label = spec.label
        val options = spec.options.orEmpty()
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false
        var expanded by remember { mutableStateOf(false) }

        val selectedLabel = options.find { it.value == currentValue }?.label ?: currentValue

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
        ) {
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !disabled) { expanded = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedLabel.ifEmpty { spec.placeholder ?: "Select..." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selectedLabel.isEmpty())
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            else MaterialTheme.colorScheme.onSurface
                        )
                        Icon(
                            imageVector = IconResolver.resolveIcon("keyboard_arrow_down"),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    options.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = option.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (option.value == currentValue) FontWeight.SemiBold else FontWeight.Normal
                                )
                            },
                            onClick = {
                                onStateChange(key, option.value)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }

    // ==================== checkbox ====================
    registry.register("checkbox") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val checked = Evaluator.toBool(state[key])
        val label = spec.label
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false

        val bgColor by animateColorAsState(
            targetValue = if (checked) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
            animationSpec = tween(200),
            label = "checkboxBg"
        )
        val contentColor by animateColorAsState(
            targetValue = when {
                !disabled.not() -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                checked -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            animationSpec = tween(200),
            label = "checkboxContent"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable(enabled = !disabled) { onStateChange(key, !checked) },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier.size(20.dp),
                color = bgColor,
                shape = RoundedCornerShape(4.dp)
            ) {
                if (checked) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
                        Icon(
                            imageVector = IconResolver.resolveIcon("check"),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = contentColor
                        )
                    }
                }
            }
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!disabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                )
            }
        }
    }

    // ==================== radio_group ====================
    registry.register("radio_group") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val currentValue = Evaluator.toStr(state[key])
        val label = spec.label
        val options = spec.options.orEmpty()
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            options.forEach { option ->
                val isSelected = option.value == currentValue
                val dotColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    animationSpec = tween(200),
                    label = "radioDot"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !disabled) { onStateChange(key, option.value) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(18.dp)
                            .border(2.dp, dotColor, RoundedCornerShape(50)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(dotColor, RoundedCornerShape(50))
                            )
                        }
                    }
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (!disabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    )
                }
            }
        }
    }

    // ==================== switch ====================
    // Matches ActionSwitch: 30dp height, 6dp corners, spring animations
    registry.register("switch") { spec, state, resolver, onStateChange, _, _ ->
        val key = spec.stateKey ?: return@register
        val checked = Evaluator.toBool(state[key])
        val label = spec.label
        val disabled = spec.disabled?.let { resolver.resolveBoolean(it, state) } ?: false

        val switchWidth = 44.dp
        val switchHeight = 26.dp
        val thumbSize = 18.dp

        val trackColor by animateColorAsState(
            targetValue = when {
                disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                checked -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
            },
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "nappSwitchTrack"
        )
        val thumbColor by animateColorAsState(
            targetValue = when {
                disabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                checked -> MaterialTheme.colorScheme.onPrimary
                else -> MaterialTheme.colorScheme.outline
            },
            animationSpec = spring(stiffness = Spring.StiffnessMedium),
            label = "nappSwitchThumb"
        )
        val thumbOffset by animateDpAsState(
            targetValue = if (checked) switchWidth - thumbSize - 4.dp else 4.dp,
            animationSpec = spring(dampingRatio = 0.65f, stiffness = 350f),
            label = "nappSwitchOffset"
        )
        val thumbScale by animateFloatAsState(
            targetValue = if (!disabled) 1f else 0.9f,
            animationSpec = spring(dampingRatio = 0.5f, stiffness = 300f),
            label = "nappSwitchScale"
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (label != null) {
                Text(
                    text = resolver.resolveString(label, state),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (!disabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                    modifier = Modifier.weight(1f)
                )
            }
            Box(
                modifier = Modifier
                    .width(switchWidth)
                    .height(switchHeight)
                    .clip(RoundedCornerShape(6.dp))
                    .background(trackColor)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = !disabled,
                        role = Role.Switch,
                        onClick = { onStateChange(key, !checked) }
                    ),
                contentAlignment = Alignment.CenterStart
            ) {
                Box(
                    modifier = Modifier
                        .offset(x = thumbOffset)
                        .size(thumbSize)
                        .scale(thumbScale)
                        .background(thumbColor, RoundedCornerShape(4.dp))
                        .border(1.dp, trackColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

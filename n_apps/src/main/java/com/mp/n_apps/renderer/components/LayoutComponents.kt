package com.mp.n_apps.renderer.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mp.n_apps.renderer.ComponentRegistry
import com.mp.n_apps.renderer.IconResolver

fun registerLayoutComponents(registry: ComponentRegistry) {

    // ==================== row ====================
    registry.register("row") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val spacing = spec.spacing ?: 6
        val alignment = when (spec.alignment) {
            "center" -> Alignment.CenterVertically
            "top" -> Alignment.Top
            "bottom" -> Alignment.Bottom
            else -> Alignment.CenterVertically
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            verticalAlignment = alignment
        ) {
            spec.children?.forEach { childId ->
                renderChild(childId)
            }
        }
    }

    // ==================== column ====================
    registry.register("column") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val spacing = spec.spacing ?: 4
        val padding = spec.padding ?: 0

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp)
        ) {
            spec.children?.forEach { childId ->
                renderChild(childId)
            }
        }
    }

    // ==================== grid ====================
    @OptIn(ExperimentalLayoutApi::class)
    registry.register("grid") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val spacing = spec.spacing ?: 6

        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(spacing.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.dp),
            maxItemsInEachRow = spec.columns ?: 2
        ) {
            spec.children?.forEach { childId ->
                Box(modifier = Modifier.weight(1f)) {
                    renderChild(childId)
                }
            }
        }
    }

    // ==================== tabs ====================
    registry.register("tabs") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val tabs = spec.tabs ?: return@register
        var selectedTabIndex by remember { mutableIntStateOf(0) }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
        ) {
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                tabs.forEachIndexed { index, tab ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (selectedTabIndex == index) FontWeight.SemiBold else FontWeight.Normal
                            )
                        },
                        icon = tab.icon?.let {
                            {
                                Icon(
                                    imageVector = IconResolver.resolveIcon(it),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    )
                }
            }

            // Tab content
            if (selectedTabIndex in tabs.indices) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs[selectedTabIndex].components.forEach { childId ->
                        renderChild(childId)
                    }
                }
            }
        }
    }

    // ==================== accordion ====================
    registry.register("accordion") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val sections = spec.sections ?: return@register

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            sections.forEach { section ->
                var expanded by remember { mutableStateOf(section.expanded) }

                val headerBg by animateColorAsState(
                    targetValue = if (expanded)
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    animationSpec = tween(200),
                    label = "accordionHeader"
                )

                Surface(
                    color = headerBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Column {
                        // Section header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { expanded = !expanded }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            section.icon?.let {
                                Icon(
                                    imageVector = IconResolver.resolveIcon(it),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = section.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                imageVector = IconResolver.resolveIcon(
                                    if (expanded) "keyboard_arrow_up" else "keyboard_arrow_down"
                                ),
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Animated content
                        AnimatedVisibility(
                            visible = expanded,
                            enter = expandVertically(),
                            exit = shrinkVertically()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp)
                                    .padding(bottom = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                section.components.forEach { childId ->
                                    renderChild(childId)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ==================== scroll_area ====================
    registry.register("scroll_area") { spec, state, resolver, onStateChange, onAction, renderChild ->
        val maxHeight = spec.maxHeight ?: 200

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 2.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            spec.children?.forEach { childId ->
                renderChild(childId)
            }
        }
    }
}

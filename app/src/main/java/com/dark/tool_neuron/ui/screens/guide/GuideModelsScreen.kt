package com.dark.tool_neuron.ui.screens.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes

@Composable
fun GuideModelsScreen(innerPadding: PaddingValues) {
    GuideDetailLayout(
        innerPadding = innerPadding,
        icon = TnIcons.Package,
        lede = "The Model Store is where you browse, download, import, and configure models. Nothing leaves your device.",
        steps = listOf(
            GuideStep(
                title = "Browse the catalog",
                body = "The Models tab lists curated HuggingFace picks across categories. Filter by quant (Q4, Q5, Q8), size, or category. Tap a card to download.",
                visual = { FilterChipsVisual() },
            ),
            GuideStep(
                title = "Add your own repo",
                body = "On the Settings tab, search HuggingFace directly or add any GGUF repo by user/repo path. The app validates before saving.",
                visual = { RepoSearchVisual() },
            ),
            GuideStep(
                title = "Import a local GGUF",
                body = "Bring Your Own Model: pick a .gguf file from your device and give it a name. Useful if you've already downloaded or fine-tuned.",
                visual = { ImportCardVisual() },
            ),
            GuideStep(
                title = "Tune the sampler",
                body = "Open any installed model → Configure. Set temperature, top-K, top-P, repetition penalty, DRY, XTC, context size, KV cache. Per-model settings persist.",
                visual = { SamplerSlidersVisual() },
            ),
            GuideStep(
                title = "Quick-start preset",
                body = "First-run picks the \"Tiny & Fast\" card — the app auto-resolves to a Q4_K_M quant so you get a small, fast model instead of full-precision bloat.",
                visual = { TinyFastCardVisual() },
            ),
        ),
        tips = listOf(
            "Active model is marked on the bottom-bar pill. Only one chat model loads at a time.",
            "Delete models you don't use — each can be hundreds of MB.",
            "GGUF is the only supported format. PyTorch / SafeTensors aren't loaded.",
        ),
    )
}

@Composable
private fun FilterChipsVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("Q4_K_M" to true, "Q5_K_M" to false, "< 1 GB" to false, "Chat" to true, "Coder" to false)
            .forEach { (label, selected) ->
                Surface(
                    shape = shapes.full,
                    color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(
                            horizontal = dimens.spacingSm,
                            vertical = dimens.spacingXxs,
                        ),
                    )
                }
            }
    }
}

@Composable
private fun RepoSearchVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingSm),
        ) {
            Icon(
                imageVector = TnIcons.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "bartowski/Llama-3.2",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary,
            ) {
                Row(
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXxs,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimens.spacingXxs),
                ) {
                    Icon(
                        imageVector = TnIcons.Plus,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "Add",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportCardVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = TnIcons.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Bring Your Own Model",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pick a .gguf file",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Icon(
                imageVector = TnIcons.ArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun SamplerSlidersVisual() {
    val dimens = LocalDimens.current
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(dimens.spacingSm),
    ) {
        SliderRow(label = "Temperature", valueText = "0.7", fillFraction = 0.35f)
        SliderRow(label = "Top-P", valueText = "0.9", fillFraction = 0.9f)
        SliderRow(label = "Rep penalty", valueText = "1.1", fillFraction = 0.2f)
    }
}

@Composable
private fun SliderRow(label: String, valueText: String, fillFraction: Float) {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Column(verticalArrangement = Arrangement.spacedBy(dimens.spacingXxs)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            )
            Text(
                text = valueText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val fullWidth = maxWidth
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
            ) {}
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .fillMaxWidth(fillFraction)
                    .height(4.dp),
            ) {}
            Box(
                modifier = Modifier
                    .padding(start = ((fullWidth * fillFraction) - 6.dp).coerceAtLeast(0.dp)),
            ) {
                Surface(
                    shape = shapes.full,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp),
                ) {}
            }
        }
    }
}

@Composable
private fun TinyFastCardVisual() {
    val dimens = LocalDimens.current
    val shapes = LocalTnShapes.current
    Surface(
        shape = shapes.card,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimens.spacingMd),
        ) {
            Icon(
                imageVector = TnIcons.Zap,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Tiny & Fast",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Auto-picks Q4_K_M quant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            Surface(
                shape = shapes.full,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
            ) {
                Text(
                    text = "~ 600 MB",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(
                        horizontal = dimens.spacingSm,
                        vertical = dimens.spacingXxs,
                    ),
                )
            }
        }
    }
}

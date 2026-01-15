package com.dark.tool_neuron.ui.screen.memory

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.tool_neuron.ui.theme.ManropeFontFamily
import com.dark.tool_neuron.ui.theme.maple
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.ui.theme.rSp
import com.memoryvault.core.VaultStats

@Composable
fun VaultStatsOverview(
    stats: VaultStats?,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (stats == null) {
        LoadingState()
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(rDp(16.dp)),
        verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
    ) {
        // Storage ring chart
        item {
            StorageOverviewCard(stats)
        }

        // Quick stats row
        item {
            QuickStatsRow(stats)
        }

        // Content breakdown
        item {
            ContentBreakdownCard(stats)
        }

        // Time range card
        item {
            TimeRangeCard(stats)
        }

        // Performance metrics
        item {
            PerformanceCard(stats)
        }
    }
}

@Composable
fun StorageOverviewCard(stats: VaultStats) {
    val usedSpace = stats.totalSizeBytes
    val wastedSpace = stats.wastedSpaceBytes
    val totalSpace = usedSpace + wastedSpace

    val usedPercent = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
    val animatedPercent by animateFloatAsState(
        targetValue = usedPercent,
        animationSpec = tween(1000),
        label = "storage_animation"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(20.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Storage Overview",
                fontSize = rSp(16.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily
            )

            Spacer(Modifier.height(rDp(20.dp)))

            // Ring chart
            Box(
                modifier = Modifier.size(rDp(160.dp)),
                contentAlignment = Alignment.Center
            ) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryColor = MaterialTheme.colorScheme.tertiary
                val trackColor = MaterialTheme.colorScheme.surfaceVariant

                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = 24.dp.toPx()
                    val radius = (size.minDimension - strokeWidth) / 2

                    // Track
                    drawCircle(
                        color = trackColor,
                        radius = radius,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Used space arc
                    drawArc(
                        color = primaryColor,
                        startAngle = -90f,
                        sweepAngle = animatedPercent * 360f,
                        useCenter = false,
                        topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                        size = Size(size.width - strokeWidth, size.height - strokeWidth),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "${(animatedPercent * 100).toInt()}%",
                        fontSize = rSp(28.sp),
                        fontWeight = FontWeight.Bold,
                        fontFamily = ManropeFontFamily
                    )
                    Text(
                        "Used",
                        fontSize = rSp(12.sp),
                        fontFamily = maple,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(rDp(20.dp)))

            // Legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StorageLegendItem(
                    color = MaterialTheme.colorScheme.primary,
                    label = "Used",
                    value = formatSize(usedSpace)
                )
                StorageLegendItem(
                    color = MaterialTheme.colorScheme.tertiary,
                    label = "Wasted",
                    value = formatSize(wastedSpace)
                )
            }
        }
    }
}

@Composable
fun StorageLegendItem(
    color: Color,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(8.dp))
    ) {
        Box(
            modifier = Modifier
                .size(rDp(12.dp))
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(
                label,
                fontSize = rSp(11.sp),
                fontFamily = maple,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = rSp(14.sp),
                fontWeight = FontWeight.SemiBold,
                fontFamily = ManropeFontFamily
            )
        }
    }
}

@Composable
fun QuickStatsRow(stats: VaultStats) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(rDp(12.dp))
    ) {
        items(
            listOf(
                QuickStat("Total Items", stats.totalItems.toString(), Icons.Outlined.Layers),
                QuickStat("Index Size", formatSize(stats.indexSizeBytes), Icons.Outlined.Storage),
                QuickStat("Compression", "${((1 - stats.compressionRatio) * 100).toInt()}%", Icons.Outlined.Compress)
            )
        ) { stat ->
            QuickStatCard(stat)
        }
    }
}

data class QuickStat(
    val label: String,
    val value: String,
    val icon: ImageVector
)

@Composable
fun QuickStatCard(stat: QuickStat) {
    Card(
        modifier = Modifier.width(rDp(120.dp)),
        shape = RoundedCornerShape(rDp(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(16.dp)),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                stat.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(24.dp)),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(rDp(8.dp)))
            Text(
                stat.value,
                fontSize = rSp(18.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily
            )
            Text(
                stat.label,
                fontSize = rSp(10.sp),
                fontFamily = maple,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun ContentBreakdownCard(stats: VaultStats) {
    val items = listOf(
        ContentTypeData("Messages", stats.messageCount, Icons.Outlined.ChatBubbleOutline, MaterialTheme.colorScheme.primary),
        ContentTypeData("Files", stats.fileCount, Icons.Outlined.InsertDriveFile, MaterialTheme.colorScheme.tertiary),
        ContentTypeData("Embeddings", stats.embeddingCount, Icons.Outlined.Hub, MaterialTheme.colorScheme.secondary),
        ContentTypeData("Custom", stats.customDataCount, Icons.Outlined.DataObject, MaterialTheme.colorScheme.error)
    )

    val total = items.sumOf { it.count }.coerceAtLeast(1)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            Text(
                "Content Breakdown",
                fontSize = rSp(16.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily
            )

            // Horizontal bar chart
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rDp(12.dp))
                    .clip(RoundedCornerShape(rDp(6.dp)))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                items.forEach { item ->
                    val fraction = item.count.toFloat() / total
                    if (fraction > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(fraction.coerceAtLeast(0.01f))
                                .background(item.color)
                        )
                    }
                }
            }

            // Legend grid
            Column(
                verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.take(2).forEach { item ->
                        ContentTypeItem(item, Modifier.weight(1f))
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    items.drop(2).forEach { item ->
                        ContentTypeItem(item, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

data class ContentTypeData(
    val label: String,
    val count: Int,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun ContentTypeItem(
    item: ContentTypeData,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(10.dp))
    ) {
        Box(
            modifier = Modifier
                .size(rDp(36.dp))
                .clip(RoundedCornerShape(rDp(10.dp)))
                .background(item.color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                modifier = Modifier.size(rDp(18.dp)),
                tint = item.color
            )
        }
        Column {
            Text(
                item.count.toString(),
                fontSize = rSp(16.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily
            )
            Text(
                item.label,
                fontSize = rSp(11.sp),
                fontFamily = maple,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun TimeRangeCard(stats: VaultStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            Text(
                "Data Timeline",
                fontSize = rSp(16.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TimelineItem(
                    icon = Icons.Outlined.History,
                    label = "Oldest Item",
                    value = if (stats.oldestItem > 0) formatTimestampFull(stats.oldestItem) else "N/A",
                    modifier = Modifier.weight(1f)
                )
                TimelineItem(
                    icon = Icons.Outlined.Update,
                    label = "Newest Item",
                    value = if (stats.newestItem > 0) formatTimestampFull(stats.newestItem) else "N/A",
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun TimelineItem(
    icon: ImageVector,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(rDp(10.dp))
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(rDp(20.dp)),
            tint = MaterialTheme.colorScheme.primary
        )
        Column {
            Text(
                label,
                fontSize = rSp(11.sp),
                fontFamily = maple,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = rSp(12.sp),
                fontWeight = FontWeight.Medium,
                fontFamily = ManropeFontFamily
            )
        }
    }
}

@Composable
fun PerformanceCard(stats: VaultStats) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(rDp(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(rDp(20.dp)),
            verticalArrangement = Arrangement.spacedBy(rDp(12.dp))
        ) {
            Text(
                "Performance Metrics",
                fontSize = rSp(16.sp),
                fontWeight = FontWeight.Bold,
                fontFamily = ManropeFontFamily
            )

            PerformanceRow(
                label = "Compression Efficiency",
                value = "${((1 - stats.compressionRatio) * 100).toInt()}%",
                progress = 1 - stats.compressionRatio
            )

            val wastedPercent = if (stats.totalSizeBytes > 0) {
                stats.wastedSpaceBytes.toFloat() / (stats.totalSizeBytes + stats.wastedSpaceBytes)
            } else 0f

            PerformanceRow(
                label = "Space Efficiency",
                value = "${((1 - wastedPercent) * 100).toInt()}%",
                progress = 1 - wastedPercent
            )
        }
    }
}

@Composable
fun PerformanceRow(
    label: String,
    value: String,
    progress: Float
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(rDp(6.dp))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                fontSize = rSp(13.sp),
                fontFamily = ManropeFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                fontSize = rSp(13.sp),
                fontFamily = maple,
                fontWeight = FontWeight.Medium
            )
        }
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(rDp(6.dp))
                .clip(RoundedCornerShape(rDp(3.dp))),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(rDp(16.dp))
        ) {
            CircularProgressIndicator()
            Text(
                "Loading vault statistics...",
                fontSize = rSp(14.sp),
                fontFamily = ManropeFontFamily,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
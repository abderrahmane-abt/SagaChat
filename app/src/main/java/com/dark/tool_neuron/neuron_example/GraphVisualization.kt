package com.dark.tool_neuron.neuron_example

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.*
import kotlin.random.Random

// ============================================================================
// Graph Layout
// ============================================================================

data class NodePosition(
    val nodeId: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f
)

/**
 * Force-directed graph layout using simple spring physics.
 */
class ForceDirectedLayout(
    private val width: Float,
    private val height: Float
) {
    private val positions = mutableMapOf<String, NodePosition>()
    private val repulsionStrength = 5000f
    private val attractionStrength = 0.01f
    private val damping = 0.85f
    private val centerGravity = 0.01f

    fun initialize(nodes: List<NeuronNode>) {
        val random = Random(42)
        val centerX = width / 2
        val centerY = height / 2
        val radius = min(width, height) * 0.35f

        nodes.forEachIndexed { index, node ->
            if (!positions.containsKey(node.id)) {
                // Arrange in a circle initially with some randomness
                val angle = (2 * PI * index / nodes.size).toFloat()
                val r = radius * (0.5f + random.nextFloat() * 0.5f)
                positions[node.id] = NodePosition(
                    nodeId = node.id,
                    x = centerX + r * cos(angle),
                    y = centerY + r * sin(angle)
                )
            }
        }

        // Remove positions for nodes that no longer exist
        val nodeIds = nodes.map { it.id }.toSet()
        positions.keys.removeAll { it !in nodeIds }
    }

    fun step(nodes: List<NeuronNode>): Boolean {
        if (nodes.isEmpty()) return false

        val nodeMap = nodes.associateBy { it.id }
        var totalMovement = 0f

        // Reset forces
        positions.values.forEach { it.vx = 0f; it.vy = 0f }

        // Repulsion between all nodes
        val positionList = positions.values.toList()
        for (i in positionList.indices) {
            for (j in i + 1 until positionList.size) {
                val p1 = positionList[i]
                val p2 = positionList[j]

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

                val force = repulsionStrength / (dist * dist)
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                p1.vx -= fx
                p1.vy -= fy
                p2.vx += fx
                p2.vy += fy
            }
        }

        // Attraction along edges
        for (node in nodes) {
            val p1 = positions[node.id] ?: continue
            for (edge in node.edges) {
                val p2 = positions[edge.targetId] ?: continue

                val dx = p2.x - p1.x
                val dy = p2.y - p1.y
                val dist = sqrt(dx * dx + dy * dy).coerceAtLeast(1f)

                // Attraction force proportional to edge weight
                val force = attractionStrength * dist * edge.weight
                val fx = (dx / dist) * force
                val fy = (dy / dist) * force

                p1.vx += fx
                p1.vy += fy
            }
        }

        // Center gravity
        val centerX = width / 2
        val centerY = height / 2
        for (pos in positions.values) {
            pos.vx += (centerX - pos.x) * centerGravity
            pos.vy += (centerY - pos.y) * centerGravity
        }

        // Apply velocities with damping
        for (pos in positions.values) {
            pos.vx *= damping
            pos.vy *= damping

            val movement = sqrt(pos.vx * pos.vx + pos.vy * pos.vy)
            totalMovement += movement

            pos.x += pos.vx
            pos.y += pos.vy

            // Keep within bounds
            pos.x = pos.x.coerceIn(50f, width - 50f)
            pos.y = pos.y.coerceIn(50f, height - 50f)
        }

        // Return true if still moving significantly
        return totalMovement > 0.5f
    }

    fun getPosition(nodeId: String): Offset? {
        return positions[nodeId]?.let { Offset(it.x, it.y) }
    }

    fun findNodeAt(x: Float, y: Float, radius: Float): String? {
        for ((id, pos) in positions) {
            val dist = sqrt((pos.x - x).pow(2) + (pos.y - y).pow(2))
            if (dist <= radius) {
                return id
            }
        }
        return null
    }
}

// ============================================================================
// Graph Visualization Composable
// ============================================================================

@Composable
fun GraphVisualization(
    nodes: List<NeuronNode>,
    selectedNodeId: String?,
    onNodeSelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var canvasSize by remember { mutableStateOf(Offset(400f, 400f)) }
    val layout = remember(canvasSize) {
        ForceDirectedLayout(canvasSize.x, canvasSize.y)
    }

    var isSimulating by remember { mutableStateOf(true) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val textMeasurer = rememberTextMeasurer()

    // Colors
    val nodeColorText = MaterialTheme.colorScheme.primary
    val nodeColorChat = MaterialTheme.colorScheme.secondary
    val nodeColorPdf = MaterialTheme.colorScheme.tertiary
    val nodeColorCustom = MaterialTheme.colorScheme.outline
    val selectedColor = MaterialTheme.colorScheme.error
    val edgeColor = MaterialTheme.colorScheme.outlineVariant
    val sequentialEdgeColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant

    // Initialize layout when nodes change
    LaunchedEffect(nodes) {
        layout.initialize(nodes)
        isSimulating = true
    }

    // Run simulation
    LaunchedEffect(isSimulating, nodes) {
        while (isSimulating && nodes.isNotEmpty()) {
            val stillMoving = layout.step(nodes)
            if (!stillMoving) {
                isSimulating = false
            }
            kotlinx.coroutines.delay(16) // ~60fps
        }
    }

    Box(
        modifier = modifier
            .background(backgroundColor, RoundedCornerShape(12.dp))
    ) {
        if (nodes.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Add content to see the graph",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(0.5f, 3f)
                            offset += pan
                        }
                    }
                    .pointerInput(nodes) {
                        detectTapGestures { tapOffset ->
                            val adjustedX = (tapOffset.x - offset.x) / scale
                            val adjustedY = (tapOffset.y - offset.y) / scale
                            val tappedNode = layout.findNodeAt(adjustedX, adjustedY, 30f)
                            onNodeSelected(tappedNode)
                        }
                    }
            ) {
                canvasSize = Offset(size.width, size.height)

                // Apply transformations
                val nodeRadius = 20f * scale
                val selectedNode = nodes.find { it.id == selectedNodeId }
                val connectedIds = selectedNode?.edges?.map { it.targetId }?.toSet() ?: emptySet()

                // Draw edges first
                for (node in nodes) {
                    val startPos = layout.getPosition(node.id) ?: continue

                    for (edge in node.edges) {
                        val endPos = layout.getPosition(edge.targetId) ?: continue

                        val isHighlighted = node.id == selectedNodeId || edge.targetId == selectedNodeId
                        val color = when {
                            isHighlighted -> selectedColor.copy(alpha = 0.8f)
                            edge.type == EdgeType.SEQUENTIAL -> sequentialEdgeColor
                            else -> edgeColor.copy(alpha = edge.weight * 0.5f)
                        }

                        val strokeWidth = when {
                            isHighlighted -> 3f * scale
                            edge.type == EdgeType.SEQUENTIAL -> 2f * scale
                            else -> 1f * scale
                        }

                        drawLine(
                            color = color,
                            start = Offset(
                                startPos.x * scale + offset.x,
                                startPos.y * scale + offset.y
                            ),
                            end = Offset(
                                endPos.x * scale + offset.x,
                                endPos.y * scale + offset.y
                            ),
                            strokeWidth = strokeWidth
                        )
                    }
                }

                // Draw nodes
                for (node in nodes) {
                    val pos = layout.getPosition(node.id) ?: continue

                    val isSelected = node.id == selectedNodeId
                    val isConnected = node.id in connectedIds

                    val nodeColor = when (node.sourceType) {
                        SourceType.TEXT -> nodeColorText
                        SourceType.CHAT -> nodeColorChat
                        SourceType.PDF -> nodeColorPdf
                        SourceType.CUSTOM -> nodeColorCustom
                    }

                    val displayColor = when {
                        isSelected -> selectedColor
                        isConnected -> nodeColor.copy(alpha = 0.9f)
                        selectedNodeId != null -> nodeColor.copy(alpha = 0.3f)
                        else -> nodeColor
                    }

                    val displayRadius = when {
                        isSelected -> nodeRadius * 1.5f
                        isConnected -> nodeRadius * 1.2f
                        else -> nodeRadius
                    }

                    val center = Offset(
                        pos.x * scale + offset.x,
                        pos.y * scale + offset.y
                    )

                    // Draw node circle
                    drawCircle(
                        color = displayColor,
                        radius = displayRadius,
                        center = center
                    )

                    // Draw border for selected/connected
                    if (isSelected || isConnected) {
                        drawCircle(
                            color = if (isSelected) selectedColor else nodeColor,
                            radius = displayRadius,
                            center = center,
                            style = Stroke(width = 2f * scale)
                        )
                    }
                }
            }
        }

        // Legend
        if (nodes.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
            ) {
                LegendItem(color = nodeColorText, label = "Text")
                LegendItem(color = nodeColorChat, label = "Chat")
                LegendItem(color = nodeColorPdf, label = "PDF")
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            drawCircle(color = color, radius = 6.dp.toPx())
        }
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ============================================================================
// Node Detail Card
// ============================================================================

@Composable
fun NodeDetailCard(
    node: NeuronNode?,
    connectedNodes: List<NeuronNode>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (node == null) return

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = node.sourceType.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = node.metadata.sourceName.ifEmpty { "Unknown" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = node.content.take(200) + if (node.content.length > 200) "..." else "",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            if (connectedNodes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Connected: ${connectedNodes.size} nodes",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                connectedNodes.take(3).forEach { connected ->
                    Text(
                        text = "- ${connected.content.take(50)}...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }

                if (connectedNodes.size > 3) {
                    Text(
                        text = "  + ${connectedNodes.size - 3} more",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Edges: ${node.edges.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "ID: ${node.id.take(8)}...",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
package com.dark.tool_neuron.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.theme.rDp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pulsing Dot Loader - 3 dots that pulse in sequence
 */
@Composable
fun PulsingDotLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulsingDots")
    
    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        repeat(3) { index ->
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 200)
                ),
                label = "dot_$index"
            )

            Canvas(modifier = Modifier.size(size)) {
                val dotRadius = size.toPx() * 0.12f
                val spacing = size.toPx() * 0.25f
                val centerX = this.size.width / 2
                val centerY = this.size.height / 2
                
                val x = centerX + (index - 1) * spacing
                
                drawCircle(
                    color = color,
                    radius = dotRadius * scale,
                    center = Offset(x, centerY)
                )
            }
        }
    }
}

/**
 * Spinning Arc - Single arc that rotates smoothly
 */
@Composable
fun SpinningArcLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = rDp(3.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "spinningArc")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 300f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Orbit Loader - 3 dots orbiting around center
 */
@Composable
fun OrbitLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orbit")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2
        val radius = size.toPx() * 0.35f
        val dotRadius = size.toPx() * 0.1f
        
        repeat(3) { index ->
            val angle = (rotation + index * 120) * PI / 180f
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            
            drawCircle(
                color = color,
                radius = dotRadius,
                center = Offset(x, y)
            )
        }
    }
}

/**
 * Pulse Ring - Single ring that expands and fades
 */
@Composable
fun PulseRingLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = rDp(2.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulseRing")
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )

    Canvas(modifier = modifier.size(size)) {
        drawCircle(
            color = color.copy(alpha = alpha),
            radius = (size.toPx() / 2) * scale,
            style = Stroke(width = strokeWidth.toPx())
        )
    }
}

/**
 * Bouncing Ball - Single ball that bounces
 */
@Composable
fun BouncingBallLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "bounce")
    val dp = rDp(2.dp)
    
    val offsetY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -size.value * 0.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "offsetY"
    )
    
    val squash by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "squash"
    )

    Canvas(modifier = modifier.size(size)) {
        val ballRadius = size.toPx() * 0.25f
        val centerX = this.size.width / 2
        val baseY = this.size.height * 0.7f
        
        // Shadow
        drawCircle(
            color = color.copy(alpha = 0.2f),
            radius = ballRadius * 0.6f,
            center = Offset(centerX, baseY + dp.toPx())
        )
        
        // Ball
        drawCircle(
            color = color,
            radius = ballRadius * squash,
            center = Offset(centerX, baseY + offsetY)
        )
    }
}

/**
 * Double Helix - Two arcs rotating in opposite directions
 */
@Composable
fun DoubleHelixLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = rDp(2.5.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "helix")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
        
        rotate(-rotation) {
            drawArc(
                color = color.copy(alpha = 0.5f),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Flower Spinner - Petals that rotate and pulse
 */
@Composable
fun FlowerSpinnerLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "flower")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Canvas(modifier = modifier.size(size)) {
        val centerX = this.size.width / 2
        val centerY = this.size.height / 2
        val petalRadius = size.toPx() * 0.15f * scale
        val distance = size.toPx() * 0.3f
        
        rotate(rotation, pivot = Offset(centerX, centerY)) {
            repeat(6) { index ->
                val angle = (index * 60) * PI / 180f
                val x = centerX + distance * cos(angle).toFloat()
                val y = centerY + distance * sin(angle).toFloat()
                
                drawCircle(
                    color = color.copy(alpha = 0.7f),
                    radius = petalRadius,
                    center = Offset(x, y)
                )
            }
        }
        
        // Center dot
        drawCircle(
            color = color,
            radius = size.toPx() * 0.1f,
            center = Offset(centerX, centerY)
        )
    }
}

/**
 * Gradient Spinner - Arc with trailing fade effect
 */
@Composable
fun GradientSpinnerLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = rDp(3.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "gradient")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            // Main arc (brightest)
            drawArc(
                color = color,
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
            
            // Trailing arcs with decreasing alpha
            drawArc(
                color = color.copy(alpha = 0.6f),
                startAngle = 90f,
                sweepAngle = 60f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
            
            drawArc(
                color = color.copy(alpha = 0.3f),
                startAngle = 150f,
                sweepAngle = 40f,
                useCenter = false,
                style = Stroke(
                    width = strokeWidth.toPx(),
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

/**
 * Morphing Shapes - Shape that morphs between circle and square
 */
@Composable
fun MorphingShapeLoader(
    modifier: Modifier = Modifier,
    size: Dp = rDp(30.dp),
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = rDp(2.5.dp)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "morph")
    
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    
    val cornerRadius by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = size.value / 2,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cornerRadius"
    )

    val dp = rDp(cornerRadius.dp)

    Canvas(modifier = modifier.size(size)) {
        rotate(rotation) {
            val rectSize = size.toPx() * 0.7f
            drawRoundRect(
                color = color,
                topLeft = Offset(
                    (this.size.width - rectSize) / 2,
                    (this.size.height - rectSize) / 2
                ),
                size = androidx.compose.ui.geometry.Size(rectSize, rectSize),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(dp.toPx()),
                style = Stroke(width = strokeWidth.toPx())
            )
        }
    }
}
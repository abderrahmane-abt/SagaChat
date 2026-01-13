package com.dark.tool_neuron.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

enum class ProgressMode {
    DETERMINATE,
    INDETERMINATE
}

@Composable
fun PixelProgressBar(
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    mode: ProgressMode = ProgressMode.DETERMINATE,
    color: Color = Color.Black,
    backgroundColor: Color = Color.LightGray.copy(alpha = 0.3f),
    rows: Int = 2,
    cornerRadius: CornerRadius = CornerRadius(2f, 2f),
    pixelSize: Dp = 4.dp,
    pixelGap: Dp = 1.dp,
    shimmerSpeed: Int = 1800,
    shimmerEnabled: Boolean = true,
    indeterminateSpeed: Int = 1200,
    indeterminateWidth: Float = 0.25f
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pixel_animation")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(shimmerSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer"
    )

    val indeterminateOffset by infiniteTransition.animateFloat(
        initialValue = -indeterminateWidth,
        targetValue = 1f + indeterminateWidth,
        animationSpec = infiniteRepeatable(
            animation = tween(indeterminateSpeed, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminate"
    )

    Canvas(modifier = modifier) {
        val size = pixelSize.toPx()
        val gap = pixelGap.toPx()
        val totalPixelSize = size + gap

        val totalPixelsX = (this.size.width / totalPixelSize).toInt()

        if (totalPixelsX <= 0 || this.size.width <= 0) return@Canvas

        // Draw background pixels
        for (y in 0 until rows) {
            for (x in 0 until totalPixelsX) {
                val xPos = x * totalPixelSize
                val yPos = y * totalPixelSize

                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset(xPos, yPos),
                    cornerRadius = cornerRadius,
                    size = Size(size, size)
                )
            }
        }

        // Draw progress pixels
        when (mode) {
            ProgressMode.DETERMINATE -> {
                val clampedProgress = progress.coerceIn(0f, 1f)
                val barWidth = this.size.width * clampedProgress
                val numPixelsX = (barWidth / totalPixelSize).toInt()

                for (y in 0 until rows) {
                    for (x in 0 until numPixelsX) {
                        val xPos = x * totalPixelSize
                        val yPos = y * totalPixelSize

                        val normalizedX = x.toFloat() / totalPixelsX.toFloat()

                        val alpha = if (shimmerEnabled && normalizedX > shimmerOffset - 0.15f &&
                            normalizedX < shimmerOffset + 0.05f) {
                            val fadeProgress = 1f - abs(normalizedX - shimmerOffset) / 0.15f
                            0.6f + (fadeProgress * 0.4f)
                        } else {
                            0.65f
                        }

                        drawRoundRect(
                            color = color.copy(alpha = alpha),
                            topLeft = Offset(xPos, yPos),
                            cornerRadius = cornerRadius,
                            size = Size(size, size)
                        )
                    }
                }
            }

            ProgressMode.INDETERMINATE -> {
                val centerPos = indeterminateOffset
                val halfWidth = indeterminateWidth / 2

                for (y in 0 until rows) {
                    for (x in 0 until totalPixelsX) {
                        val xPos = x * totalPixelSize
                        val yPos = y * totalPixelSize

                        val normalizedX = x.toFloat() / totalPixelsX.toFloat()
                        val distanceFromCenter = abs(normalizedX - centerPos)

                        if (distanceFromCenter < halfWidth) {
                            val fadeProgress = 1f - (distanceFromCenter / halfWidth)
                            val alpha = 0.35f + (fadeProgress * 0.65f)

                            drawRoundRect(
                                color = color.copy(alpha = alpha),
                                topLeft = Offset(xPos, yPos),
                                cornerRadius = cornerRadius,
                                size = Size(size, size)
                            )
                        }
                    }
                }
            }
        }
    }
}
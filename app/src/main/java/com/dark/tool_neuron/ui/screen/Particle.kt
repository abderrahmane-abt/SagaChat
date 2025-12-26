package com.dark.tool_neuron.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.components.PixelProgressBar
import com.dark.tool_neuron.ui.components.ProgressMode

@Composable
fun AIAppScreen(paddingValues: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(paddingValues)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        var progress by remember { mutableStateOf(0f) }
        var isLoading by remember { mutableStateOf(false) }

        // Indeterminate Progress Bar
        Text("Indeterminate Mode (Loading)")
        PixelProgressBar(
            modifier = Modifier.fillMaxWidth(),
            mode = ProgressMode.INDETERMINATE,
            color = Color(0xFF6200EE),
            backgroundColor = Color(0xFFE0E0E0),
            rows = 2,
            cornerRadius = CornerRadius(2f, 2f),
            pixelSize = 8.dp,
            pixelGap = 3.dp,
            indeterminateSpeed = 1200,
            indeterminateWidth = 0.3f
        )

        // Determinate Progress Bar with shimmer
        Text("Determinate Mode (${(progress * 100).toInt()}%)")
        PixelProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            mode = ProgressMode.DETERMINATE,
            color = Color(0xFF03DAC5),
            backgroundColor = Color(0xFFE0E0E0),
            rows = 2,
            cornerRadius = CornerRadius(2f, 2f),
            pixelSize = 8.dp,
            pixelGap = 3.dp,
            shimmerSpeed = 1500
        )

        // Static Progress Bar (no animation)
        Text("Static Progress (60%)")
        PixelProgressBar(
            progress = 0.6f,
            modifier = Modifier.fillMaxWidth(),
            mode = ProgressMode.DETERMINATE,
            color = Color(0xFFFF6B6B),
            backgroundColor = Color(0xFFE0E0E0),
            rows = 3,
            cornerRadius = CornerRadius(4f, 4f),
            pixelSize = 6.dp,
            pixelGap = 2.dp,
            shimmerEnabled = false // Disable shimmer cleanly
        )

        // Retro style progress bar
        Text("Retro Style")
        PixelProgressBar(
            progress = progress,
            modifier = Modifier.fillMaxWidth(),
            mode = ProgressMode.DETERMINATE,
            color = Color(0xFF00FF00),
            backgroundColor = Color(0xFF003300),
            rows = 1,
            cornerRadius = CornerRadius.Zero,
            pixelSize = 12.dp,
            pixelGap = 4.dp
        )

        // Control buttons
        Button(
            onClick = {
                if (isLoading) {
                    isLoading = false
                } else {
                    progress = 0f
                    isLoading = true
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isLoading) "Stop Loading" else "Start Loading")
        }

        Button(
            onClick = { progress = 0f },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reset Progress")
        }

        // Simulate progress loading
        LaunchedEffect(isLoading) {
            while (isLoading) {
                kotlinx.coroutines.delay(50)
                progress = (progress + 0.01f).coerceIn(0f, 1f)
                if (progress >= 1f) {
                    isLoading = false
                }
            }
        }
    }
}
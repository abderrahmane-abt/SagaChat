package com.moorixlabs.sagachat.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.moorixlabs.sagachat.ui.screens.theme_preview.ColorShowcaseScreen
import com.moorixlabs.sagachat.ui.theme.ColorPalette
import com.moorixlabs.sagachat.ui.theme.SagaChatTheme

class ColorShowcaseActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val paletteRaw = intent.getStringExtra(EXTRA_PALETTE) ?: ColorPalette.DYNAMIC.name
        val isDark = intent.getBooleanExtra(EXTRA_DARK, true)
        val palette = runCatching { ColorPalette.valueOf(paletteRaw) }.getOrDefault(ColorPalette.DYNAMIC)

        setContent {
            SagaChatTheme(darkTheme = isDark, palette = palette) {
                ColorShowcaseScreen(paletteName = palette.displayName, isDark = isDark)
            }
        }
    }

    companion object {
        const val EXTRA_PALETTE = "palette"
        const val EXTRA_DARK = "dark"
    }
}

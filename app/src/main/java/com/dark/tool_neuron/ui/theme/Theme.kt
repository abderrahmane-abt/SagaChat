package com.dark.tool_neuron.ui.theme

import android.annotation.SuppressLint
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rDp(
    baseDp: Dp, designWidth: Float = 360f, minDp: Dp? = null, maxDp: Dp? = null
): Dp {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()

    // Prevent division by zero and handle edge cases
    if (designWidth <= 0f || screenWidthDp <= 0f) return baseDp

    val scaleFactor = screenWidthDp / designWidth

    // Apply scaling with optional clamping
    var scaledValue = baseDp.value * scaleFactor

    // Clamp to min/max if provided
    minDp?.let { scaledValue = maxOf(scaledValue, it.value) }
    maxDp?.let { scaledValue = minOf(scaledValue, it.value) }

    return scaledValue.dp
}

@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rSp(
    baseSp: TextUnit,
    designWidth: Float = 360f,
    minSp: TextUnit? = null,
    maxSp: TextUnit? = null,
    respectFontScale: Boolean = true
): TextUnit {
    // Handle non-SP units gracefully
    if (baseSp.type != TextUnitType.Sp) return baseSp

    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = config.screenWidthDp.toFloat()

    // Prevent division by zero and handle edge cases
    if (designWidth <= 0f || screenWidthDp <= 0f) return baseSp

    val scale = screenWidthDp / designWidth

    // Scaled value (still in sp units)
    var scaledValue = baseSp.value * scale

    // If you want to IGNORE accessibility fontScale (rare), neutralize it
    if (!respectFontScale) {
        val fontScale = density.fontScale.coerceAtLeast(0.001f)
        scaledValue /= fontScale
    }

    // Clamp if provided - fixed: use minOf instead of min for consistency
    minSp?.let {
        if (it.type == TextUnitType.Sp) {
            scaledValue = maxOf(scaledValue, it.value)
        }
    }
    maxSp?.let {
        if (it.type == TextUnitType.Sp) {
            scaledValue = minOf(scaledValue, it.value)
        }
    }

    return scaledValue.sp
}



@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = if (darkTheme) {
        dynamicDarkColorScheme(context)
    } else {
        dynamicLightColorScheme(context)
    }

    // Define typography with Manrope as the base/default family
    val typography = Typography().copy(
        displayLarge = Typography().displayLarge.copy(fontFamily = ManropeFontFamily),
        displayMedium = Typography().displayMedium.copy(fontFamily = ManropeFontFamily),
        displaySmall = Typography().displaySmall.copy(fontFamily = ManropeFontFamily),
        headlineLarge = Typography().headlineLarge.copy(fontFamily = ManropeFontFamily),
        headlineMedium = Typography().headlineMedium.copy(fontFamily = ManropeFontFamily),
        headlineSmall = Typography().headlineSmall.copy(fontFamily = ManropeFontFamily),
        titleLarge = Typography().titleLarge.copy(fontFamily = ManropeFontFamily),
        titleMedium = Typography().titleMedium.copy(fontFamily = ManropeFontFamily),
        titleSmall = Typography().titleSmall.copy(fontFamily = ManropeFontFamily),
        bodyLarge = Typography().bodyLarge.copy(fontFamily = ManropeFontFamily),
        bodyMedium = Typography().bodyMedium.copy(fontFamily = ManropeFontFamily),
        bodySmall = Typography().bodySmall.copy(fontFamily = ManropeFontFamily),
        labelLarge = Typography().labelLarge.copy(fontFamily = ManropeFontFamily),
        labelMedium = Typography().labelMedium.copy(fontFamily = ManropeFontFamily),
        labelSmall = Typography().labelSmall.copy(fontFamily = ManropeFontFamily),
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = typography,
        motionScheme = MotionScheme.expressive(),
        content = content
    )
}
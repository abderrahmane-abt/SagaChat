package com.dark.neuroverse.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isUnspecified
import androidx.compose.ui.unit.sp
import kotlin.math.min


private val DarkColorScheme = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Grey,
    background = Black,
    onBackground = White,
    surface = LightBlack,
    onSurface = White,
    primaryContainer = PrimaryContainer
)
private val BoldColorScheme = lightColorScheme(
    primary = Color(0xFF1258CE),         // Bright blue
    onPrimary = Color(0xFFFFFFFF),       // White
    secondary = Color(0xFF8B5CF6),       // Purple accent
    background = Color(0xFFF8FAFC),      // Very light blue
    onBackground = Color(0xFF0F172A),    // Almost black
    surface = Color(0xFFFFFFFF),         // White
    onSurface = Color(0xFF1E293B),       // Dark slate
    primaryContainer = Color(0xFF2563EB) // Medium blue
)

private val LightColorScheme = lightColorScheme(
    // Primary roles — main brand identity
    primary = Color(0xFF696FC7),       // Brand accent (buttons, highlights)
    onPrimary = Color(0xFFFFFFFF),     // Text/icons on primary

    // Secondary roles — supporting visuals
    secondary = Color(0xFFA7AAE1),     // For chips, secondary buttons
    onSecondary = Color(0xFF1C1C1C),   // Text/icons on secondary

    // Tertiary roles — decorative, playful accents
    tertiary = Color(0xFFF2AEBB),      // Use sparingly for highlights, icons, or illustrations
    onTertiary = Color(0xFF1C1C1C),

    // Background and surface roles — large areas
    background = Color(0xFFFFFBFE),    // Light neutral background (Material baseline)
    onBackground = Color(0xFF000000),

    surface = Color(0xFFFFFFFF),       // Cards, sheets, app bars
    onSurface = Color(0xFF000000),

    // Container versions for filled components (elevated buttons, FABs, etc.)
    primaryContainer = Color(0xFFE2E4FF),
    onPrimaryContainer = Color(0xFF1A1C54),

    secondaryContainer = Color(0xFFE7E9FF),
    onSecondaryContainer = Color(0xFF232555),

    tertiaryContainer = Color(0xFFF5D3C4),
    onTertiaryContainer = Color(0xFF3A1C17),

    // Error colors — standard Material defaults
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)


/**
 * Scales a base dp size based on current screen width (responsive).
 *
 * @param baseDp The original size you designed for (e.g., 360dp width screen).
 * @param designWidth The screen width your design is based on (default 360dp).
 */
@SuppressLint("ConfigurationScreenWidthHeight")
@Composable
fun rDP(baseDp: Dp, designWidth: Float = 360f): Dp {
    val config = LocalConfiguration.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val scaleFactor = screenWidthDp / designWidth
    return (baseDp.value * scaleFactor).dp
}

// responsive sp (width-based), keeps system fontScale by default
@Composable
fun rSp(
    baseSp: TextUnit,
    designWidth: Float = 360f,
    minSp: TextUnit? = null,
    maxSp: TextUnit? = null,
    respectFontScale: Boolean = true
): TextUnit {
    val config = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidthDp = config.screenWidthDp.toFloat()
    val scale = screenWidthDp / designWidth

    // scaled value (still in sp units)
    var v = baseSp.value * scale

    // if you want to IGNORE accessibility fontScale (rare), neutralize it:
    if (!respectFontScale) {
        v /= density.fontScale.coerceAtLeast(0.001f)
    }

    // clamp if provided
    minSp?.let { v = maxOf(v, it.value) }
    maxSp?.let { v = min(v, maxSp.value) }

    return v.sp
}

// optional: shortest-dimension scaling (better for orientation changes)
@Composable
fun rSpShortest(baseSp: TextUnit, designShortSide: Float = 360f): TextUnit {
    val cfg = LocalConfiguration.current
    val shortSide = min(cfg.screenWidthDp, cfg.screenHeightDp).toFloat()
    val scale = shortSide / designShortSide
    return (baseSp.value * scale).sp
}

// convenience to scale an existing TextStyle’s fontSize if set
@Composable
fun TextStyle.scaled(designWidth: Float = 360f): TextStyle =
    if (fontSize.isUnspecified) this else copy(fontSize = rSp(fontSize, designWidth))


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true, // toggle if you ever want static fallback
    content: @Composable () -> Unit
) {
    val context = LocalContext.current

    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            // Android 12–14: regular dynamic colors
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        }

        else -> {
            // Fallback to your static colors
            if (darkTheme) DarkColorScheme else LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        motionScheme = MotionScheme.expressive(), // expressive motion = subtle, fluid animations
        content = content
    )
}


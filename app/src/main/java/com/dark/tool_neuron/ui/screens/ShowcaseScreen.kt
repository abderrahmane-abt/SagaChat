@file:OptIn(ExperimentalFoundationApi::class)

package com.dark.tool_neuron.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// ════════════════════════════════════════════
//  SHOWCASE DATA
// ════════════════════════════════════════════

private data class ShowcasePage(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val accent: String,
    val blobColor1: Color,
    val blobColor2: Color,
    val blobCenter: Offset   // normalized 0..1 within canvas
)

private val PAGES = listOf(
    ShowcasePage(
        icon = Icons.Filled.Face,
        title = "Hey, Welcome!",
        subtitle = "Your privacy-first AI assistant.\nEverything runs on your device.",
        accent = "No cloud. No telemetry. Just you.",
        blobColor1 = Color(0xFF8B7EC8),
        blobColor2 = Color(0xFFC4B5FD),
        blobCenter = Offset(0.5f, 0.3f)
    ),
    ShowcasePage(
        icon = Icons.Filled.Lock,
        title = "Your Data, Your Rules",
        subtitle = "AES-256 encryption with a passphrase\nonly you know. No recovery, no backdoors.",
        accent = "Encrypted \u00B7 Offline \u00B7 Private",
        blobColor1 = Color(0xFF0D9488),
        blobColor2 = Color(0xFF5EEAD4),
        blobCenter = Offset(0.6f, 0.25f)
    ),
    ShowcasePage(
        icon = Icons.Filled.Star,
        title = "Intelligence On-Device",
        subtitle = "Run AI models directly on your phone.\nNo internet needed. No compromise.",
        accent = "Fully offline inference",
        blobColor1 = Color(0xFFD97706),
        blobColor2 = Color(0xFFFCD34D),
        blobCenter = Offset(0.4f, 0.3f)
    ),
    ShowcasePage(
        icon = Icons.Filled.Favorite,
        title = "It Remembers, It Learns",
        subtitle = "Personas, memories, and knowledge\nthat grow with you over time.",
        accent = "Your assistant, your way",
        blobColor1 = Color(0xFFE11D48),
        blobColor2 = Color(0xFFFDA4AF),
        blobCenter = Offset(0.5f, 0.28f)
    )
)

// ════════════════════════════════════════════
//  MAIN SCREEN
// ════════════════════════════════════════════

@Composable
fun ShowcaseScreen(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { PAGES.size })
    val scope = rememberCoroutineScope()
    val pageCount = PAGES.size

    // Blob animation time
    val blobTime = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        blobTime.animateTo(
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 60_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    // Interpolated blob color
    val currentPage = pagerState.currentPage
    val offset = pagerState.currentPageOffsetFraction
    val fromPage = PAGES[currentPage]
    val toPage = PAGES[(currentPage + 1).coerceAtMost(pageCount - 1)]
    val fraction = offset.coerceIn(0f, 1f)

    val blobC1 by animateColorAsState(
        targetValue = lerpColor(fromPage.blobColor1, toPage.blobColor1, fraction),
        animationSpec = tween(100),
        label = "blobC1"
    )
    val blobC2 by animateColorAsState(
        targetValue = lerpColor(fromPage.blobColor2, toPage.blobColor2, fraction),
        animationSpec = tween(100),
        label = "blobC2"
    )
    val blobCx = lerp(fromPage.blobCenter.x, toPage.blobCenter.x, fraction)
    val blobCy = lerp(fromPage.blobCenter.y, toPage.blobCenter.y, fraction)

    // Button accent color
    val accentColor by animateColorAsState(
        targetValue = PAGES[currentPage].blobColor1,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "accent"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
    ) {
        // Layer 1: Morphing gradient blob
        MorphingBlob(
            time = blobTime.value,
            color1 = blobC1,
            color2 = blobC2,
            centerX = blobCx,
            centerY = blobCy,
            modifier = Modifier.fillMaxSize()
        )

        // Layer 2: Pager content
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val pageData = PAGES[page]
            val pageOffset = pagerState.currentPageOffsetFraction +
                (pagerState.currentPage - page)

            ShowcasePageContent(
                page = pageData,
                pageOffset = pageOffset,
                isSettled = abs(pageOffset) < 0.01f
            )
        }

        // Layer 3: Navigation overlay
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
        ) {
            // Skip button row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (currentPage < pageCount - 1) {
                    TextButton(onClick = onFinished) {
                        Text(
                            "Skip",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Bottom: dots + button
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Page indicator dots
                PageDots(
                    count = pageCount,
                    current = currentPage,
                    accentColor = accentColor
                )

                // Action button — animated on page change
                val isLastPage = currentPage == pageCount - 1
                AnimatedContent(
                    targetState = isLastPage,
                    transitionSpec = {
                        (slideInHorizontally { it / 3 } + fadeIn(tween(250)))
                            .togetherWith(slideOutHorizontally { -it / 3 } + fadeOut(tween(150)))
                    },
                    label = "btnTransition"
                ) { lastPage ->
                    Button(
                        onClick = {
                            if (lastPage) {
                                onFinished()
                            } else {
                                scope.launch {
                                    pagerState.animateScrollToPage(currentPage + 1)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text(
                            text = if (lastPage) "Get Started" else "Next",
                            fontWeight = FontWeight.SemiBold
                        )
                        if (!lastPage) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════
//  PAGE CONTENT
// ════════════════════════════════════════════

@Composable
private fun ShowcasePageContent(
    page: ShowcasePage,
    pageOffset: Float,
    isSettled: Boolean
) {
    // Parallax at 3 different rates for depth
    val iconOffsetX = (pageOffset * 20f).roundToInt()
    val titleOffsetX = (pageOffset * 50f).roundToInt()
    val subtitleOffsetX = (pageOffset * 90f).roundToInt()
    val accentOffsetX = (pageOffset * 130f).roundToInt()
    val contentAlpha = 1f - abs(pageOffset).coerceAtMost(1f) * 0.7f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp)
            .alpha(contentAlpha),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Icon — parallax slowest
        Box(
            modifier = Modifier
                .size(88.dp)
                .offset { IntOffset(iconOffsetX, 0) },
            contentAlignment = Alignment.Center
        ) {
            if (isSettled) {
                Icon(
                    imageVector = page.icon,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = page.blobColor1
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // Title — slow parallax
        Text(
            text = page.title,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            letterSpacing = (-0.3).sp,
            modifier = Modifier.offset { IntOffset(titleOffsetX, 0) }
        )

        Spacer(Modifier.height(10.dp))

        // Subtitle — medium parallax
        Text(
            text = page.subtitle,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 23.sp,
            modifier = Modifier.offset { IntOffset(subtitleOffsetX, 0) }
        )

        Spacer(Modifier.height(14.dp))

        // Accent badge — fast parallax, tinted chip feel
        Text(
            text = page.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = page.blobColor1.copy(alpha = 0.85f),
            textAlign = TextAlign.Center,
            letterSpacing = 0.4.sp,
            modifier = Modifier.offset { IntOffset(accentOffsetX, 0) }
        )
    }
}

// ════════════════════════════════════════════
//  PAGE DOTS
// ════════════════════════════════════════════

@Composable
private fun PageDots(count: Int, current: Int, accentColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until count) {
            val isActive = i == current
            val width by animateDpAsState(
                targetValue = if (isActive) 24.dp else 8.dp,
                animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                label = "dotW$i"
            )
            val color by animateColorAsState(
                targetValue = if (isActive) accentColor else
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                animationSpec = spring(stiffness = Spring.StiffnessMedium),
                label = "dotC$i"
            )
            Box(
                modifier = Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
        }
    }
}

// ════════════════════════════════════════════
//  MORPHING BLOB
// ════════════════════════════════════════════

@Composable
private fun MorphingBlob(
    time: Float,
    color1: Color,
    color2: Color,
    centerX: Float,
    centerY: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val cx = size.width * centerX
        val cy = size.height * centerY
        val baseRadius = size.minDimension * 0.38f

        // Outer blob: 12 points, 3 harmonics per point
        val outerPath = buildBlobPath(cx, cy, baseRadius, 12, time, 0f)
        drawPath(
            path = outerPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    color1.copy(alpha = 0.30f),
                    color2.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(cx, cy),
                radius = baseRadius * 1.6f
            )
        )

        // Inner blob: smaller, offset phase, higher alpha for depth
        val innerCx = cx + sin(time * 0.3f) * baseRadius * 0.06f
        val innerCy = cy + cos(time * 0.4f) * baseRadius * 0.06f
        val innerPath = buildBlobPath(innerCx, innerCy, baseRadius * 0.6f, 10, time, 2f)
        drawPath(
            path = innerPath,
            brush = Brush.radialGradient(
                colors = listOf(
                    color1.copy(alpha = 0.18f),
                    color2.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(innerCx, innerCy),
                radius = baseRadius * 0.9f
            )
        )
    }
}

private fun buildBlobPath(
    cx: Float, cy: Float, baseRadius: Float,
    points: Int, time: Float, phaseOffset: Float
): Path {
    val coords = FloatArray(points * 2)
    for (i in 0 until points) {
        val angle = (i.toFloat() / points) * 2f * Math.PI.toFloat()
        // 3 harmonics with unique freq/phase per point for organic movement
        val h1 = sin(time * 0.7f + i * 1.7f + phaseOffset) * 0.12f
        val h2 = cos(time * 0.45f + i * 2.5f + phaseOffset) * 0.08f
        val h3 = sin(time * 1.1f + i * 0.9f + phaseOffset * 1.5f) * 0.05f
        val r = baseRadius * (1f + h1 + h2 + h3)
        coords[i * 2] = cx + cos(angle) * r
        coords[i * 2 + 1] = cy + sin(angle) * r
    }

    val path = Path()
    path.moveTo(coords[0], coords[1])
    for (i in 0 until points) {
        val next = (i + 1) % points
        val midX = (coords[i * 2] + coords[next * 2]) / 2f
        val midY = (coords[i * 2 + 1] + coords[next * 2 + 1]) / 2f
        path.quadraticTo(
            coords[i * 2], coords[i * 2 + 1],
            midX, midY
        )
    }
    path.close()
    return path
}

// Helpers

private fun lerpColor(a: Color, b: Color, t: Float): Color = Color(
    red = lerp(a.red, b.red, t),
    green = lerp(a.green, b.green, t),
    blue = lerp(a.blue, b.blue, t),
    alpha = lerp(a.alpha, b.alpha, t)
)

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

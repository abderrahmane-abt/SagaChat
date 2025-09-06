package com.dark.neuroverse.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionContext
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.platform.findViewTreeCompositionContext
import androidx.compose.ui.unit.DpSize
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun captureComposableToBitmap(
    context: Context,
    parentComposition: CompositionContext?,      // <- important
    widthPx: Int,
    heightPx: Int,
    content: @Composable () -> Unit
): Bitmap = withContext(Dispatchers.Main) {       // must be on Main
    val w = widthPx.coerceAtLeast(1)
    val h = heightPx.coerceAtLeast(1)

    val composeView = ComposeView(context).apply {
        // Use parent composition instead of WindowRecomposer
        if (parentComposition != null) setParentCompositionContext(parentComposition)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        layoutParams = FrameLayout.LayoutParams(w, h)
        setContent { content() }
    }

    // Give Compose one frame to run the initial composition
    withFrameNanos { /* just await a frame */ }

    val wSpec = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY)
    val hSpec = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY)
    composeView.measure(wSpec, hSpec)
    composeView.layout(0, 0, w, h)

    createBitmap(w, h).also { bmp ->
        val canvas = android.graphics.Canvas(bmp)
        composeView.draw(canvas)
    }
}


@Composable
fun ProjectedCapturable(
    size: DpSize,
    captureKey: Any?,                 // change to trigger
    captureWhen: Boolean = true,
    onCaptured: (Bitmap) -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val parentComposition = LocalView.current.findViewTreeCompositionContext()

    // 1) Show UI on screen
    Box(Modifier.size(size)) { content() }

    // 2) Capture off-screen when asked
    LaunchedEffect(captureKey, captureWhen, size) {
        if (!captureWhen || parentComposition == null) return@LaunchedEffect
        val w = with(density) { size.width.roundToPx() }.coerceAtLeast(1)
        val h = with(density) { size.height.roundToPx() }.coerceAtLeast(1)
        val bmp = captureComposableToBitmap(
            context = context,
            parentComposition = parentComposition, // <- critical line
            widthPx = w,
            heightPx = h,
            content = content
        )
        onCaptured(bmp)
    }
}

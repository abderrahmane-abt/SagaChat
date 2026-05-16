package com.dark.tool_neuron.service.island

import android.content.Context
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView

/**
 * FrameLayout host so we can attach setViewTreeXxxOwner once at the parent
 * level and let the inner ComposeView pick it up via parent traversal.
 * No touch interception — the window resizes between pill bounds and
 * full-screen so unrelated taps simply miss the window entirely in pill mode.
 */
class IslandComposeView(context: Context) : FrameLayout(context) {

    private val composeView = ComposeView(context).also {
        addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setContent(content: @Composable () -> Unit) {
        composeView.setContent(content)
    }
}

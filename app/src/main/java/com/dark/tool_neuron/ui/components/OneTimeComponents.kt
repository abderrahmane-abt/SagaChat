package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.dark.tool_neuron.R
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedTitle() {
    var titleType by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2000)
            titleType = if (titleType == 0) 1 else 0
        }
    }

    AnimatedContent(targetState = titleType, transitionSpec = {
        (fadeIn(
            animationSpec = tween(520)
        )) togetherWith (fadeOut(
            animationSpec = tween(520)
        ))
    }, label = "TopBarTitleAnim") { state ->
        when (state) {
            0 -> TitleRow(
                text = "Hey, Welcome User", icon = R.drawable.user
            )

            else -> TitleRow(
                text = "Ask Your Query", icon = R.drawable.vl_models
            )
        }
    }

}
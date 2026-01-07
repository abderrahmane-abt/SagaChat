package com.dark.tool_neuron.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.SubdirectoryArrowLeft
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.ui.theme.rDp
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedTitle() {
    var titleType by remember { mutableIntStateOf(0) }

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


@Composable
fun ModelListItem(
    modifier: Modifier,
    model: Model,
    isLoaded: Boolean,
    onClickListener: (model: Model) -> Unit
) {
    Card(
        modifier, colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(0.04f)
        )
    ) {
        Row(
            modifier = Modifier.padding(rDp(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(model.modelName)
            Spacer(Modifier.weight(1f))
            Crossfade(isLoaded) {
                when (it) {
                    true -> {
                        ActionTextButton(onClickListener = {
                            onClickListener(model)
                        }, Icons.Default.SubdirectoryArrowLeft, text = "Unload")
                    }

                    false -> {
                        ActionButton(onClickListener = {
                            onClickListener(model)
                        }, Icons.Default.ArrowOutward)
                    }
                }
            }
        }
    }
}
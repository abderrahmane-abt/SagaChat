package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.global.Standards
import com.dark.tool_neuron.models.state.AppState
import com.dark.tool_neuron.models.state.getBackgroundColor
import com.dark.tool_neuron.models.state.getColor
import com.dark.tool_neuron.models.state.getContentColor
import com.dark.tool_neuron.ui.theme.rDp

@Composable
fun TitleRow(
    text: String, icon: Int, state: AppState, modifier: Modifier = Modifier
) {
    val iconColor = state.getColor()
    val backgroundColor = state.getBackgroundColor()
    val contentColor = state.getContentColor()

    Surface(
        color = backgroundColor,
        shape = RoundedCornerShape(rDp(26.dp)),
        modifier = modifier.height(rDp(Standards.ActionIconSize))
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(rDp(6.dp)),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(end = rDp(12.dp))
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .padding(start = rDp(12.dp))
                    .size(rDp(18.dp)) // Standard icon size
            )
            Text(
                text = text,
                color = contentColor,
                maxLines = 1,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}
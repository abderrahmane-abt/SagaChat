package com.dark.tool_neuron.ui.screen.home_screen

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.R
import com.dark.tool_neuron.ui.theme.rDp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreenTopBar() {
    CenterAlignedTopAppBar(navigationIcon = {
        IconButton(
            onClick = {  },
            shape = RoundedCornerShape(rDp(8.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colorScheme.secondary.copy(0.1f),
                contentColor = colorScheme.secondary
            )
        ) {
            Icon(painter = painterResource(R.drawable.menu), contentDescription = "Menu")
        }
    }, title = {
        ModelSelection()
    }, actions = {
        IconButton(
            onClick = {  },
            shape = RoundedCornerShape(rDp(8.dp)),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = colorScheme.secondary.copy(0.1f),
                contentColor = colorScheme.secondary
            )
        ) {
            Icon(painter = painterResource(R.drawable.settings), contentDescription = "Menu")
        }
    })
}

@Composable
fun ModelSelection(){
    var isCompact by remember { mutableStateOf(false) }
    var selectedModelName by remember { mutableStateOf("Model Name") }


    Column {
        if (isCompact) {
            IconButton(
                onClick = {

                },
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = colorScheme.secondary.copy(0.1f),
                    contentColor = colorScheme.secondary
                ),
                shape = RoundedCornerShape(rDp(8.dp)),
            ) {
                Icon(painterResource(R.drawable.ai_model), "Model")
            }
        } else {
            Button(
                onClick = {  },
                colors = ButtonDefaults.buttonColors(
                    containerColor = colorScheme.secondary.copy(0.1f),
                    contentColor = colorScheme.secondary
                ),
                shape = RoundedCornerShape(rDp(8.dp)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ai_model), "Model")
                    Spacer(modifier = Modifier.width(rDp(8.dp)))
                    Text(
                        text = selectedModelName, maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(rDp(8.dp)))
                    Icon(painterResource(R.drawable.down), "Expand")
                }
            }
        }
    }
}


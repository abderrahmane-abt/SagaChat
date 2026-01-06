package com.dark.tool_neuron.ui.screen.home_screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.R
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.AnimatedTitle
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ThemeViewModel

@Composable
fun HomeScreen() {

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar()
        },
        bottomBar = {
            BottomBar()
        }) { paddingValues ->
        BodyContent(paddingValues)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    CenterAlignedTopAppBar(title = {
        AnimatedTitle()
    }, navigationIcon = {
        ActionButton(onClickListener = {

        }, R.drawable.menu, modifier = Modifier.padding(start = rDp(6.dp)))
    }, actions = {
        ActionButton(onClickListener = {

        }, R.drawable.settings, modifier = Modifier.padding(end = rDp(6.dp)))
    })
}


@Composable
fun BodyContent(
    paddingValues: PaddingValues,
    themeViewModel: ThemeViewModel = AppContainer.getThemeViewModel()
) {
    val isDarkTheme by themeViewModel.isDarkTheme.collectAsStateWithLifecycle()
    val colorScheme = if (isDarkTheme) {
        MaterialTheme.colorScheme.background
    } else {
        MaterialTheme.colorScheme.onBackground
    }
    val finalTheme by animateColorAsState(colorScheme)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(finalTheme)
            .padding(paddingValues)
    ) {

    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomBar(themeViewModel: ThemeViewModel = AppContainer.getThemeViewModel()) {
    var value by remember { mutableStateOf("") }

    Box(
        Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.primary.copy(0.04f)
                    .compositeOver(MaterialTheme.colorScheme.background)
            )
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = rDp(8.dp))
                .padding(top = rDp(8.dp), bottom = rDp(10.dp))

        ) {

            Row(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = rDp(200.dp))
            ) {
                TextField(
                    value = value, onValueChange = {
                    value = it
                }, modifier = Modifier.weight(1f), placeholder = {
                    Text(text = "Say Anything…")
                }, colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    cursorColor = MaterialTheme.colorScheme.primary
                )
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(rDp(6.dp))) {
                ActionButton(onClickListener = {

                }, R.drawable.tool, modifier = Modifier.padding(start = 12.dp))

                ActionButton(onClickListener = {
                    themeViewModel.setDarkTheme(!themeViewModel.isDarkTheme.value)
                }, R.drawable.smart_temp_message, modifier = Modifier.padding(start = 12.dp))

                Spacer(Modifier.weight(1f))

                ActionButton(
                    onClickListener = {

                    },
                    R.drawable.send_chat,
                    shape = MaterialShapes.Ghostish.toShape(),
                    modifier = Modifier.padding(end = 12.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary.copy(0.3f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        }
    }
}
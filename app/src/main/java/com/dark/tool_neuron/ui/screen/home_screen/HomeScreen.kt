package com.dark.tool_neuron.ui.screen.home_screen

import android.content.Intent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dark.tool_neuron.R
import com.dark.tool_neuron.activity.ModelLoadingActivity
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.ui.components.ActionButton
import com.dark.tool_neuron.ui.components.ActionToggleButton
import com.dark.tool_neuron.ui.components.AnimatedTitle
import com.dark.tool_neuron.ui.components.ModelListItem
import com.dark.tool_neuron.ui.theme.rDp
import com.dark.tool_neuron.viewmodel.ChatViewModel
import com.dark.tool_neuron.viewmodel.LLMModelViewModel

@Composable
fun HomeScreen(chatViewModel: ChatViewModel = viewModel()) {

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopBar()
        },
        bottomBar = {
            BottomBar {
                chatViewModel.sendMessage(it)
            }
        }) { paddingValues ->
        BodyContent(paddingValues)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopBar() {
    val context = LocalContext.current

    CenterAlignedTopAppBar(title = {
        AnimatedTitle()
    }, navigationIcon = {
        ActionButton(onClickListener = {

        }, R.drawable.menu, modifier = Modifier.padding(start = rDp(6.dp)))
    }, actions = {
        ActionButton(onClickListener = {
            context.startActivity(Intent(context, ModelLoadingActivity::class.java))
        }, R.drawable.settings, modifier = Modifier.padding(end = rDp(6.dp)))
    })
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun BottomBar(
    viewModel: LLMModelViewModel = viewModel(
        factory = AppContainer.getLLMModelViewModelFactory()
    ),
    onSendClick: (String) -> Unit = {}
) {
    var value by remember { mutableStateOf("") }
    val installedModels by viewModel.installedModels.collectAsStateWithLifecycle(emptyList())
    val currentModelID by viewModel.currentModelID.collectAsStateWithLifecycle()
    var showModelList by remember { mutableStateOf(false) }


    Column {
        AnimatedVisibility(showModelList) {
            LazyColumn(
                Modifier
                    .fillMaxWidth()
                    .padding(rDp(8.dp))
                    .background(
                        MaterialTheme.colorScheme.primary.copy(0.04f)
                            .compositeOver(MaterialTheme.colorScheme.background),
                        shape = RoundedCornerShape(rDp(8.dp))
                    ),
                contentPadding = PaddingValues(bottom = rDp(8.dp))
            ) {
                items(installedModels) { modelConfig ->
                    ModelListItem(
                        Modifier.padding(top = rDp(8.dp)).padding(horizontal = rDp(8.dp)),
                        isLoaded = currentModelID == modelConfig.id,
                        model = modelConfig
                    ) { selectedModel ->
                        viewModel.loadModel(model = selectedModel)
                    }
                }
            }
        }

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

                    ActionToggleButton(
                        onCheckedChange = {
                            showModelList = !showModelList
                        },
                        checked = showModelList,
                        icon = R.drawable.smart_temp_message,
                        modifier = Modifier.padding(start = 12.dp)
                    )

                    Spacer(Modifier.weight(1f))

                    ActionButton(
                        onClickListener = {
                            onSendClick(value)
                            value = ""
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

}
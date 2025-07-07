package com.dark.neuroverse.compose.screens.assistant

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dark.mylibrary.STTManager
import com.dark.neuroverse.R
import com.dark.neuroverse.compose.components.GlitchTypingText
import com.dark.neuroverse.neurov.mcp.chat.models.ROLE
import com.dark.neuroverse.neurov.mcp.chat.viewModels.ChattingViewModel
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.neuroverse.utils.UserPrefs
import com.dark.neuroverse.utils.extractPureJson
import com.dark.neuroverse.viewModel.NeuroVScreenViewModel
import com.dark.task_manager.register.TaskRegistry
import com.dark.task_manager.register.TaskRouter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject


private val cardColor = Color(0xFFEFEFEF)

@Composable
fun NeuroVScreen(onClickOutside: () -> Unit) {
    val viewModel = remember { NeuroVScreenViewModel() }
    var action by remember { mutableStateOf(Action.NONE) }

    NeuroVerseTheme {
        Column(
            modifier = ComposeConfig.NeuroVScreen_rootModifier.clickable {
                onClickOutside()
            },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            Column(
                modifier = ComposeConfig.NeuroVScreen_holderModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NeuroVHeader(action, onBack = { action = Action.NONE })
                NeuroVBody(action, onClick = { action = it }, viewModel)
                NeuroVBottomBar(action, viewModel, plg = { })
            }
        }
    }
}

@Composable
internal fun NeuroVHeader(action: Action, onBack: () -> Unit = {}) {
    Row(
        ComposeConfig.NeuroVHeader_rootModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {

        AnimatedVisibility(action != Action.NONE) {
            Icon(
                painterResource(R.drawable.back),
                contentDescription = "Send",
                modifier = Modifier
                    .size(40.dp)
                    .clickable { onBack() }
            )
        }

        Spacer(Modifier.width(20.dp))

        Text(
            "Neuro V",
            style = MaterialTheme.typography.headlineMedium,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            color = Color.Black,
            modifier = Modifier
                .padding(vertical = 16.dp)
                .weight(1f)
        )

        IconButton (
            onClick = {
                //Open Settings Activity
            },
            modifier = Modifier.padding(end = 16.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black,
                contentColor = Color.White
            )
        ) {
            Icon(
                painter = painterResource(R.drawable.settings), contentDescription = "Settings"
            )
        }
    }
}

@Composable
internal fun NeuroVBody(
    action: Action,
    onClick: (action: Action) -> Unit,
    viewModel: NeuroVScreenViewModel
) {
    AnimatedContent(action, transitionSpec = {
        (fadeIn()).togetherWith(fadeOut())
    }, label = "Action") {
        when (it) {
            Action.NONE -> ComposeComponents.BodyContentNone(onClick)

            Action.SPEAK -> ComposeComponents.BodyContentSTT()

            else -> ComposeComponents.ResultComposable(viewModel, action)
        }
    }
}

@Composable
internal fun NeuroVBottomBar(action: Action, viewModel: NeuroVScreenViewModel, plg: () -> Unit) {
    Row(
        ComposeConfig.NeuroVBottomBar_rootModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ActionBox(action, viewModel, plg)
    }
}

@Composable
internal fun BottomNavButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 12.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.Black else Color.White)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(
            text,
            color = if (selected) Color.White else Color.Black,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}

@Composable
internal fun ActionBox(action: Action, viewModel: NeuroVScreenViewModel, onPluginSelected: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedContent(action, transitionSpec = {
            (fadeIn()).togetherWith(fadeOut())
        }, label = "Action") {
            when (it) {
                Action.WRITE -> ComposeComponents.BottomBarActionWrite(viewModel)

                Action.SPEAK -> ComposeComponents.BodyContentSTT()

                else -> ComposeComponents.DefaultActionCompose(onPluginSelected)
            }
        }
    }
}

internal object ComposeConfig {

    val NeuroVScreen_rootModifier = Modifier
        .fillMaxSize()
        .padding(horizontal = 12.dp)
        .padding(bottom = 34.dp)

    val NeuroVScreen_holderModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(24.dp))
        .background(Color.White)
        .padding(20.dp)


    val NeuroVHeader_rootModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(12.dp, 12.dp, 6.dp, 6.dp))
        .background(cardColor)


    val NeuroVBottomBar_rootModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(6.dp, 6.dp, 12.dp, 12.dp))
        .background(cardColor)

}

internal object ComposeComponents {

    @Composable
    internal fun QuickActionCard(
        modifier: Modifier = Modifier,
        icon: Painter,
        title: String,
        desc: String,
        isCheckable: Boolean = false,
        onClick: () -> Unit = {}
    ) {
        var checked by remember { mutableStateOf(false) }
        val context = LocalContext.current

        LaunchedEffect(Unit) {
            UserPrefs.isAGU(context).collect {
                checked = it
            }
        }

        LaunchedEffect(checked) {
            UserPrefs.setAGU(context, checked)
        }

        val animColor = animateColorAsState(
            if (checked) Color(0xFF0FB100) else Color.Black, animationSpec = tween(
                durationMillis = 500, easing = FastOutSlowInEasing
            )
        )

        val highlightColor = if (isCheckable) animColor.value else Color.Black

        Card(
            modifier,
            shape = RoundedCornerShape(6.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = cardColor
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(10.dp)
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                        .clickable {
                            if (isCheckable) {
                                checked = !checked
                            } else {
                                Log.d("QuickActionCard", "Clicked")
                                onClick()
                            }
                        },
                ) {
                    Column(
                        modifier = Modifier.size(84.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            painter = icon,
                            contentDescription = title,
                            tint = highlightColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = if (highlightColor != Color.Unspecified) highlightColor else Color.Black,
                                fontWeight = FontWeight.SemiBold
                            ),
                        )
                    }
                }

                Text(
                    desc,
                    style = MaterialTheme.typography.bodySmall,
                    lineHeight = 14.sp,
                    maxLines = 3,
                    textAlign = TextAlign.Center,
                    fontFamily = FontFamily.Serif,
                    modifier = Modifier.padding(),
                    color = Color.DarkGray
                )
            }
        }
    }

    @Composable
    fun BodyContentNone(onClick: (action: Action) -> Unit) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.typing),
                title = "Write To AI",
                desc = "Feel to be Private..? Try Typing Your Task To AI....",
                onClick = {
                    onClick(Action.WRITE)
                }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.mic),
                title = "Speak..!",
                desc = "No Need To Type, Just Click And Let the Magic Happen",
                onClick = {
                    onClick(Action.SPEAK)
                }
            )
            QuickActionCard(
                modifier = Modifier.weight(1f),
                icon = painterResource(R.drawable.brain), // swap for your AGU icon
                title = "AGU",
                desc = "Let the AI understand the surrounding & make decisions",
                true
            )
        }
    }

    @Composable
    fun BodyContentSTT() =
        Toast.makeText(LocalContext.current, "Coming Soon....!", Toast.LENGTH_SHORT).show()

    @Composable
    fun ResultComposable(viewModel: NeuroVScreenViewModel, action: Action) {
        val scrollState = rememberScrollState()

        when (action) {
            Action.TASKS -> {}

            else -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(cardColor)
                        .verticalScroll(scrollState)
                ) {
                    val resultText = viewModel.result.collectAsState().value.optString("result", "")

                    GlitchTypingText(
                        finalText = resultText,
                        delayPerChar = 1L,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 12.dp)
                    )
                }
            }
        }
    }

    @Composable
    fun BottomBarActionWrite(viewModel: NeuroVScreenViewModel) {
        var text by remember { mutableStateOf("Search About Humans") }
        var isAguChecked by remember { mutableStateOf(false) }
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()

        // Collect AGU preference once
        LaunchedEffect(Unit) {
            UserPrefs.isAGU(context).collect { isAguChecked = it }
        }

        val animColor by animateColorAsState(
            targetValue = if (isAguChecked) Color(0xFF0FB100) else Color.Black,
            animationSpec = tween(durationMillis = 500, easing = FastOutSlowInEasing)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val context = LocalContext.current
            val stt = remember(context) { STTManager(context) }
            val speechResults by stt.speechResults.collectAsState()

            LaunchedEffect(speechResults) {
                text = speechResults
            }

            LaunchedEffect(speechResults) {
                text = speechResults
            }

            BasicTextField(
                modifier = Modifier.weight(1f),
                value = text,
                onValueChange = { text = it },
                decorationBox = { innerTextField ->
                    Box {
                        if (text.isEmpty()) {
                            Text(
                                "Write Message Here...",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                        innerTextField()
                    }
                }
            )

            // AUG
            Icon(
                painterResource(R.drawable.brain),
                contentDescription = "AGU",
                tint = animColor,
                modifier = Modifier.clickable {
                    isAguChecked = !isAguChecked
                    coroutineScope.launch {
                        UserPrefs.setAGU(context, isAguChecked)
                    }
                }
            )

            // SPEAK
            Icon(
                painterResource(R.drawable.mic),
                contentDescription = "Speak",
                modifier = Modifier.clickable {
                    if (stt.isModelReady()) {
                        if(stt.isListening.value) stt.stop()
                        else stt.startListening()
                    } else {
                        Toast.makeText(context, "Model loading...", Toast.LENGTH_SHORT).show()
                    }
                }
            )


            //SEND
            Icon(
                Icons.AutoMirrored.Outlined.Send,
                contentDescription = "Send",
                modifier = Modifier.clickable {
                    if (text.isNotBlank()) {
                        val safePrompt = text
                        text = ""

                        coroutineScope.launch(Dispatchers.IO) {
                            val raw = TaskRouter.processUserPrompt(safePrompt)
                            Log.d("TaskDemoScreen", "Raw output: $raw")
                            val jsonText = extractPureJson(raw)

                            try {
                                val jsonObject = JSONObject(jsonText)
                                val toolCall = jsonObject.getJSONObject("tool_call")
                                val args = toolCall.getJSONObject("args")

                                TaskRegistry.startTask(toolCall.getString("name"), args) {
                                    viewModel.updateResult(it)
                                }
                            } catch (e: Exception) {
                                Log.e("TaskRouter", "Failed to parse tool_call JSON: ${e.message}")
                            }
                        }
                    }
                }
            )
        }
    }

    @Composable
    fun DefaultActionCompose(onPluginSelected: () -> Unit) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(cardColor), verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Plugins Actions",
                modifier = Modifier.padding(horizontal = 24.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold
            )

            VerticalDivider(modifier = Modifier.height(50.dp), thickness = 2.dp)

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(start = 10.dp)
            ) {
                items(4) { it ->
                    BottomNavButton(it.toString(), selected = false) {
                        onPluginSelected()
                    }
                }
            }
        }
    }
}

internal enum class Action {
    NONE, WRITE, SPEAK, TASKS
}
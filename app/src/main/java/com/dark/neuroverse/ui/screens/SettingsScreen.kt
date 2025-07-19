package com.dark.neuroverse.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.dark.ai_module.model.ModelsData
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.data.UserPrefs
import com.dark.neuroverse.ui.theme.rDP
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onResetTweaks: () -> Unit = {},
    onClearUserData: () -> Unit = {},
    appVersion: String = "0.0.1",
    onUpdateApp: () -> Unit = {}
) {
    val innerCorner = 8.dp
    val outerCorner = 20.dp


    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item {
            Text(
                "NeuroV Settings", style = MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold
                )
            )
        }

        // ---- MODEL SETTINGS ----
        item {

            var professionalism by remember { mutableFloatStateOf(2f) }
            var emotionalTone by remember { mutableFloatStateOf(7f) }
            val currentModel = ModelManager.getModel().collectAsState()
            val context = LocalContext.current
            var expanded by remember { mutableStateOf(false) }

            LocalDensity.current
            LocalWindowInfo.current.containerSize.width.dp
            val modelList = remember { mutableStateListOf<ModelsData>() }

            LaunchedEffect(Unit) {
                val updatedProfessionalism =
                    UserPrefs.getModelPParams(context).firstOrNull() ?: 2.5f
                val updatedEmotionalTone = UserPrefs.getModelEParams(context).firstOrNull() ?: 7.3f

                professionalism = updatedProfessionalism
                emotionalTone = updatedEmotionalTone

                ModelManager.observeModels().collectLatest { data ->
                    modelList.clear()
                    modelList += data
                    Log.d("ModelManager", "Model list updated: $data")
                }
            }

            LaunchedEffect(professionalism, emotionalTone) {
                UserPrefs.setModelPParams(context, professionalism)
                UserPrefs.setModelEParams(context, emotionalTone)

                // Save the updated values to shared preferences or perform any other necessary actions 
                // Update the slider values when the preferences change
                val updatedProfessionalism =
                    UserPrefs.getModelPParams(context).firstOrNull() ?: 2.5f
                val updatedEmotionalTone = UserPrefs.getModelEParams(context).firstOrNull() ?: 7.3f

                professionalism = updatedProfessionalism
                emotionalTone = updatedEmotionalTone
            }


            Text(
                "Model Settings",
                modifier = Modifier.padding(vertical = 12.dp),
                style = MaterialTheme.typography.headlineMedium.copy(fontFamily = FontFamily.Serif)
            )

            SettingCard(
                title = "Current Model", roundedCornerShape = RoundedCornerShape(
                    topStart = outerCorner,
                    topEnd = outerCorner,
                    bottomEnd = innerCorner,
                    bottomStart = innerCorner
                ), actionLabel = "Switch", onAction = {
                    expanded = true
                }) {
                if (expanded) {
                    ModelDialog(modelList, context) {
                        expanded = false
                        if (it != null) {
                            ModelManager.loadModel(context, it) {

                            }
                        }
                    }
                }


                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Name: ")
                        }
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Normal)) {
                            append("${currentModel.value.modeName}\n\n")
                        }

                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Parameters\n")
                        }

                        append("\u2023 Context Size: ${currentModel.value.modelCtxSize}\n")
                        append("\u2023 Model Size: ${currentModel.value.modelSize} MB\n")
                        append("\u2023 Tool Call: ${currentModel.value.toolUse}")
                    }, modifier = Modifier.padding(12.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            SettingCard(
                title = "Model Tweaks", roundedCornerShape = RoundedCornerShape(
                    topStart = innerCorner,
                    topEnd = innerCorner,
                    bottomEnd = outerCorner,
                    bottomStart = outerCorner
                ), actionLabel = "Reset", onAction = onResetTweaks
            ) {
                Spacer(Modifier.height(8.dp))
                Text("Professionalism : 0.1 - 9.0")
                Slider(
                    value = professionalism, onValueChange = {
                        professionalism = it
                    }, valueRange = 0.1f..9.0f, colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    ), modifier = Modifier.fillMaxWidth(), steps = 9
                )
                Spacer(Modifier.height(8.dp))
                Text("Emotional : 0.1 - 9.0")
                Slider(
                    value = emotionalTone, onValueChange = {
                        emotionalTone = it
                    }, valueRange = 0.1f..9.0f, colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surface
                    ), modifier = Modifier.fillMaxWidth(), steps = 9
                )
            }
        }

        // ---- USER SETTINGS ----
        item {
            Text("User Settings", style = MaterialTheme.typography.titleLarge)
            SettingCard(
                title = "Clear User Data", actionLabel = "Clear", onAction = onClearUserData
            )
        }

        // ---- APP SETTINGS ----
        item {
            Text("App Settings", style = MaterialTheme.typography.titleLarge)
            SettingCard(
                title = "App Version : $appVersion", actionLabel = "Update", onAction = onUpdateApp
            ) {
                Text(
                    buildAnnotatedString {
                        withStyle(style = SpanStyle(fontWeight = FontWeight.Bold)) {
                            append("Whats New ..?\n")
                        }

                        append("\u2023 Context Size: \n")
                        append("\u2023 Model Size: \n")
                        append("\u2023 Tool Call: ")
                    }, modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(18.dp),
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(roundedCornerShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction, colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    ), modifier = Modifier.height(rDP(28.dp))
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SettingCard(
    title: String,
    actionLabel: String? = null,
    roundedCornerShape: RoundedCornerShape = RoundedCornerShape(18.dp),
    onAction: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit = { }
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(roundedCornerShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (actionLabel != null && onAction != null) {
                Button(
                    onClick = onAction, colors = ButtonDefaults.buttonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                        containerColor = MaterialTheme.colorScheme.background
                    ), modifier = Modifier.height(rDP(28.dp))
                ) {
                    Text(actionLabel)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(Modifier.padding(12.dp)) {
                content()
            }
        }
    }
}


@Composable
fun ModelDialog(
    modelList: List<ModelsData>,
    context: Context,
    onDismissRequest: (ModelsData?) -> Unit
) {
    Dialog(
        onDismissRequest = { onDismissRequest(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false) // full-width on small screens
    ) {
        AnimatedVisibility(
            visible = true, enter = fadeIn(animationSpec = tween(300)) + scaleIn(
                initialScale = 0.9f, animationSpec = tween(300)
            ), exit = fadeOut(animationSpec = tween(200)) + scaleOut(
                targetScale = 0.9f, animationSpec = tween(200)
            )
        ) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .wrapContentHeight()
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .padding(24.dp)
                        .wrapContentHeight()
                ) {
                    // Title
                    Text(
                        text = "Select Model",
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Scrollable model list
                    Column(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        modelList.forEachIndexed { index, model ->
                            Button(
                                onClick = {
                                    onDismissRequest(model)
                                    ModelManager.loadModel(context, model) {}
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                shape = MaterialTheme.shapes.large
                            ) {
                                Text(
                                    text = model.modeName,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }

                            if (index != modelList.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 8.dp),
                                    thickness = 0.5.dp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Cancel button
                    TextButton(
                        onClick = { onDismissRequest(null) },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        }
    }
}
package com.dark.neuroverse.activity

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import com.dark.ai_module.model.ModelData
import com.dark.neuroverse.ui.theme.NeuroVerseTheme

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVerseTheme {
                ModelEditorScreen(ModelData(), onSave = {}, onBack = {})
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelEditorScreen(
    model: ModelData, onSave: (ModelData) -> Unit, onBack: () -> Unit
) {
    var modelState by remember { mutableStateOf(model) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Model Editor", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { onSave(modelState) }) {
                        Icon(Icons.Default.Check, contentDescription = "Save")
                    }
                })
        }) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(scrollState)
                .fillMaxSize()
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedSection(title = "Performance Settings") {
                PerformanceEditor(modelState) { modelState = it }
            }

            AnimatedSection(title = "Sampling Controls") {
                SamplingEditor(modelState) { modelState = it }
            }

            AnimatedSection(title = "Text Behavior") {
                BehaviorEditor(modelState) { modelState = it }
            }

            AnimatedSection(title = "Prompt Configuration") {
                PromptEditor(modelState) { modelState = it }
            }
        }
    }
}


@Composable
fun AnimatedSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    var expanded by remember { mutableStateOf(true) }
    val rotation by animateFloatAsState(
        if (expanded) 180f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        )
    )
    val elevation by animateFloatAsState(
        targetValue = if (expanded) 12f else 4f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(elevation.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    title, style = MaterialTheme.typography.titleMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier
                        .rotate(rotation)
                        .padding(4.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded, enter = fadeIn(spring(dampingRatio = 0.8f)) + expandVertically(
                    spring(dampingRatio = 0.6f)
                ), exit = fadeOut(spring(dampingRatio = 0.8f)) + shrinkVertically(
                    spring(dampingRatio = 0.7f)
                )
            ) {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content
                )
            }
        }
    }
}


@Composable
fun PerformanceEditor(model: ModelData, onChange: (ModelData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SliderSetting(
            label = "Threads",
            value = model.threads.toFloat(),
            range = 1f..Runtime.getRuntime().availableProcessors().toFloat(),
            steps = 0
        ) { onChange(model.copy(threads = it.toInt())) }

        SliderSetting("GPU Layers", model.gpuLayers.toFloat(), 0f..60f) {
            onChange(model.copy(gpuLayers = it.toInt()))
        }

        SettingSwitch("Use MMAP", model.useMMAP) {
            onChange(model.copy(useMMAP = it))
        }
        SettingSwitch("Use MLOCK", model.useMLOCK) {
            onChange(model.copy(useMLOCK = it))
        }

        SliderSetting("Context Size", model.ctxSize.toFloat(), 512f..8192f, step = 512f) {
            onChange(model.copy(ctxSize = it.toInt()))
        }
    }
}


@Composable
fun SamplingEditor(model: ModelData, onChange: (ModelData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SliderSetting("Temperature", model.temp, 0f..2f, step = 0.1f) {
            onChange(model.copy(temp = it))
        }
        SliderSetting("Top K", model.topK.toFloat(), 0f..100f) {
            onChange(model.copy(topK = it.toInt()))
        }
        SliderSetting("Top P", model.topP, 0f..1f) {
            onChange(model.copy(topP = it))
        }
        SliderSetting("Min P", model.minP, 0f..1f) {
            onChange(model.copy(minP = it))
        }
        SliderSetting("Max Tokens", model.maxTokens.toFloat(), 256f..8192f, step = 256f) {
            onChange(model.copy(maxTokens = it.toInt()))
        }
    }
}


@Composable
fun BehaviorEditor(model: ModelData, onChange: (ModelData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        DropdownSetting(
            label = "Mirostat Mode",
            options = listOf("Off", "v1", "v2"),
            selectedIndex = model.mirostat
        ) { onChange(model.copy(mirostat = it)) }

        SliderSetting("Tau (Target Entropy)", model.mirostatTau, 1f..10f, 0.1f) {
            onChange(model.copy(mirostatTau = it))
        }

        SliderSetting("Eta (Learning Rate)", model.mirostatEta, 0.01f..1f, 0.01f) {
            onChange(model.copy(mirostatEta = it))
        }

        SliderSetting("Seed", model.seed.toFloat(), -1f..9999f, step = 1f) {
            onChange(model.copy(seed = it.toInt()))
        }
    }
}


@Composable
fun PromptEditor(model: ModelData, onChange: (ModelData) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = model.systemPrompt,
            onValueChange = { onChange(model.copy(systemPrompt = it)) },
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = model.chatTemplate ?: "",
            onValueChange = { onChange(model.copy(chatTemplate = it)) },
            label = { Text("Chat Template") },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun SliderSetting(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float = 0f,
    steps: Int = 0,
    onValueChange: (Float) -> Unit
) {
    Column {
        Text("$label: ${"%.2f".format(value)}")
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range,
            steps = steps.takeIf { step == 0f }
                ?: ((range.endInclusive - range.start) / step).toInt())
    }
}

@Composable
fun SettingSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSetting(
    label: String, options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = options[selectedIndex],
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { i, opt ->
                DropdownMenuItem(onClick = {
                    expanded = false
                    onSelect(i)
                }, text = { Text(opt) })
            }
        }
    }
}

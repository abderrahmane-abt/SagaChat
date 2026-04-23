package com.dark.tool_neuron.ui.screens.setup_screen

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.ui.components.ActionTextButton
import com.dark.tool_neuron.ui.components.ActionToggleGroup
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.model_store.ModelImportTypePicker
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.ui.theme.LocalTnShapes
import com.dark.tool_neuron.ui.theme.Motion
import kotlinx.coroutines.delay

private enum class SetupPath(val label: String) {
    QuickStart("Quick Start"),
    PowerUser("Power User")
}

@Composable
fun ModelSetupScreen(
    innerPadding: PaddingValues,
    onModelSelected: (modelId: String) -> Unit,
    onOpenStore: () -> Unit,
    onLocalImport: (uri: Uri, name: String, size: Long, type: ProviderType) -> Unit,
    onSkip: () -> Unit,
) {
    val dimens = LocalDimens.current
    val context = LocalContext.current
    var visible by remember { mutableStateOf(false) }
    var selectedPath by remember { mutableStateOf(SetupPath.QuickStart) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<Triple<Uri, String, Long>?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            var name = "model.gguf"
            var size = 0L
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
            pendingImport = Triple(uri, name, size)
        }
    }

    LaunchedEffect(Unit) { delay(80); visible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(horizontal = dimens.screenPadding),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .widthIn(max = 480.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Center
    ) {
        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 4 }
        ) {
            Column {
                Icon(
                    imageVector = TnIcons.Rocket,
                    contentDescription = null,
                    modifier = Modifier.size(dimens.iconLg),
                    tint = MaterialTheme.colorScheme.primary
                )

                Spacer(Modifier.height(dimens.spacingLg))

                Text(
                    text = "Get your first model",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(Modifier.height(dimens.spacingXs))

                Text(
                    text = "Choose a path that fits you",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(dimens.spacingXl))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 3 }
        ) {
            Column {
                ActionToggleGroup(
                    items = SetupPath.entries,
                    selectedItem = selectedPath,
                    onItemSelected = { selectedPath = it },
                    itemLabel = { it.label }
                )

                Spacer(Modifier.height(dimens.spacingXl))

                when (selectedPath) {
                    SetupPath.QuickStart -> {
                        ModelCard(
                            icon = TnIcons.Zap,
                            title = "Tiny & Fast",
                            subtitle = "LFM2 350M · Q4_K_M · ~200MB · Best for low-RAM",
                            selected = selectedModel == "lfm25-350m",
                            onClick = { selectedModel = "lfm25-350m" }
                        )

                        Spacer(Modifier.height(dimens.spacingSm))

                        ModelCard(
                            icon = TnIcons.Leaf,
                            title = "Balanced",
                            subtitle = "Qwen3 0.6B · Q4_K_M · ~400MB · Good all-rounder",
                            selected = selectedModel == "qwen3-0.6b",
                            onClick = { selectedModel = "qwen3-0.6b" }
                        )

                        Spacer(Modifier.height(dimens.spacingSm))

                        ModelCard(
                            icon = TnIcons.Download,
                            title = "Bring Your Own",
                            subtitle = "Import a local .gguf file",
                            selected = selectedModel == "import",
                            onClick = { selectedModel = "import" }
                        )

                        Spacer(Modifier.height(dimens.spacingXl))

                        ActionTextButton(
                            onClickListener = {
                                when (selectedModel) {
                                    "import" -> filePicker.launch(arrayOf("application/octet-stream", "*/*"))
                                    null -> {}
                                    else -> onModelSelected(selectedModel!!)
                                }
                            },
                            icon = TnIcons.ArrowRight,
                            text = "Continue",
                            enabled = selectedModel != null
                        )
                    }

                    SetupPath.PowerUser -> {
                        Text(
                            text = "Head to the Model Store to browse hundreds of models, configure repos, and pick exactly what you need.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(Modifier.height(dimens.spacingLg))

                        ActionTextButton(
                            onClickListener = onOpenStore,
                            icon = TnIcons.Rocket,
                            text = "Open Model Store"
                        )
                    }
                }
            }
        }

        Spacer(Modifier.weight(1f))

        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(Motion.entrance()) + slideInVertically(Motion.entrance()) { it / 2 }
        ) {
            Text(
                text = "Skip for now",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSkip)
                    .padding(vertical = dimens.spacingLg)
            )
        }
    }
    }

    pendingImport?.let { (uri, name, size) ->
        ModelImportTypePicker(
            fileName = name,
            onPick = { type ->
                onLocalImport(uri, name, size, type)
                pendingImport = null
            },
            onDismiss = { pendingImport = null },
        )
    }
}

@Composable
private fun ModelCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val tnShapes = LocalTnShapes.current
    val dimens = LocalDimens.current

    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = Motion.state(),
        label = "modelBorder"
    )

    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
        else MaterialTheme.colorScheme.surface,
        animationSpec = Motion.state(),
        label = "modelBg"
    )

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = tnShapes.card,
        color = containerColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier.padding(dimens.spacingMd),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(dimens.iconLg)
            )
            Spacer(Modifier.width(dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

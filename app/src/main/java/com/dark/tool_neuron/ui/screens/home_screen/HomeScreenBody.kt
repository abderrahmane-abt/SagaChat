package com.dark.tool_neuron.ui.screens.home_screen

import android.content.Intent
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.ui.components.action_window.ActionWindowOverlay
import com.dark.tool_neuron.ui.theme.LocalDimens
import com.dark.tool_neuron.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    actionWindowExpanded: Boolean,
    onActionWindowDismiss: () -> Unit,
    onStoreClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onOpenModelManager: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val lastCrash by InferenceClient.lastCrashInfo.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val dimens = LocalDimens.current
    val chatDocuments by viewModel.chatDocuments.collectAsStateWithLifecycle()
    val currentChatId by viewModel.currentChatId.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streamingFragment.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val installedModels by viewModel.chatModels.collectAsStateWithLifecycle()
    val modelLoadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
    val generationStatus by viewModel.generationStatus.collectAsStateWithLifecycle()
    val activeModel by viewModel.activeModel.collectAsStateWithLifecycle()
    val contextUsage by viewModel.contextUsage.collectAsStateWithLifecycle()
    val lastTextMetrics by viewModel.lastTextMetrics.collectAsStateWithLifecycle()
    val lastMemoryMetrics by viewModel.lastMemoryMetrics.collectAsStateWithLifecycle()

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            var fileName = "Document"
            var fileSize = 0L
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = it.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = it.getString(nameIdx) ?: "Document"
                    if (sizeIdx >= 0) fileSize = it.getLong(sizeIdx)
                }
            }
            val mimeType = context.contentResolver.getType(uri)
            viewModel.addDocument(uri, fileName, fileSize, mimeType)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        ChatMessageList(
            messages = messages,
            streaming = streaming,
            isGenerating = isGenerating,
            generationStatus = generationStatus,
            onRegenerate = viewModel::regenerateLast,
            onDelete = viewModel::deleteMessage,
            onEditUserMessage = viewModel::editUserMessage,
            contentPadding = PaddingValues(
                horizontal = dimens.spacingLg,
                vertical = dimens.spacingMd,
            ),
        )

        ActionWindowOverlay(
            expanded = actionWindowExpanded,
            onDismiss = onActionWindowDismiss,
            installedModels = installedModels,
            modelLoadState = modelLoadState,
            activeModel = activeModel,
            contextUsage = contextUsage,
            lastTextMetrics = lastTextMetrics,
            lastMemoryMetrics = lastMemoryMetrics,
            onLoadModel = { viewModel.loadModel(it) },
            onUnloadModel = { viewModel.unloadModel() },
            onStoreClick = onStoreClick,
            onGuideClick = onGuideClick,
            onSettingsClick = onSettingsClick,
            onLoadDocument = {
                filePicker.launch(arrayOf(
                    "text/*",
                    "application/pdf",
                    "application/json",
                    "application/xml",
                    "application/rtf",
                    "application/epub+zip",
                    "application/vnd.oasis.opendocument.text",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                ))
            },
            documents = chatDocuments,
            onRemoveDocument = { viewModel.removeDocument(it) },
        )

        lastCrash?.let { crash ->
            InferenceCrashDialog(
                crash = crash,
                onDismiss = { InferenceClient.dismissCrashInfo() },
                onOpenModelManager = onOpenModelManager,
            )
        }
    }
}

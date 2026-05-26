package com.moorixlabs.sagachat.ui.screens.home_screen

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.ui.theme.LocalDimens
import com.moorixlabs.sagachat.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    innerPadding: PaddingValues,
    onOpenModelManager: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel(),
    modifier: Modifier = Modifier,
) {
    val lastCrash by InferenceClient.lastCrashInfo.collectAsStateWithLifecycle()
    val dimens = LocalDimens.current
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val streaming by viewModel.streamingFragment.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val generationStatus by viewModel.generationStatus.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(innerPadding),
    ) {
        ChatMessageList(
            messages = messages,
            streaming = streaming,
            isGenerating = isGenerating,
            generationStatus = generationStatus,
            speakingMessageId = null,
            loadingSpeakId = null,
            canSpeak = false,
            onSpeakToggle = { _, _ -> },
            onRegenerate = viewModel::regenerateLast,
            onDelete = viewModel::deleteMessage,
            onEditMessage = viewModel::editMessage,
            onForkFromMessage = viewModel::forkFromMessage,
            onCancelWebSearch = {},
            retrievalLabel = null,
            contentPadding = PaddingValues(
                horizontal = dimens.spacingLg,
                vertical = dimens.spacingMd,
            ),
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

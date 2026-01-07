package com.dark.tool_neuron.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.worker.LlmModelWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    // Use mutableStateListOf for efficient item updates during streaming
    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentAssistantMessageId: String? = null
    private var currentAssistantMessageIndex: Int = -1

    fun sendMessage(prompt: String, maxTokens: Int = 512) {
        if (!LlmModelWorker.isModelLoaded.value) {
            _error.value = "Please load a model first"
            return
        }

        if (_isGenerating.value) {
            return
        }

        val userMessage = Messages(
            role = Role.User,
            content = MessageContent(
                contentType = ContentType.Text,
                content = prompt
            )
        )
        _messages.add(userMessage)

        generate(prompt, maxTokens)
    }

    private fun generate(prompt: String, maxTokens: Int) {
        viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null

            val assistantMessage = Messages(
                role = Role.Assistant,
                content = MessageContent(contentType = ContentType.Text, content = "")
            )
            currentAssistantMessageId = assistantMessage.msgId
            currentAssistantMessageIndex = _messages.size
            _messages.add(assistantMessage)

            try {
                LlmModelWorker.ggufGenerateStreaming(prompt, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            // Direct index update - only updates one item
                            if (currentAssistantMessageIndex >= 0 &&
                                currentAssistantMessageIndex < _messages.size) {
                                val current = _messages[currentAssistantMessageIndex]
                                _messages[currentAssistantMessageIndex] = current.copy(
                                    content = current.content.copy(
                                        content = current.content.content + event.text
                                    )
                                )
                            }
                        }
                        is GenerationEvent.Done -> {
                            _isGenerating.value = false
                            currentAssistantMessageId = null
                            currentAssistantMessageIndex = -1
                        }
                        is GenerationEvent.Error -> {
                            _isGenerating.value = false
                            _error.value = event.message
                            if (currentAssistantMessageIndex >= 0 &&
                                currentAssistantMessageIndex < _messages.size) {
                                val current = _messages[currentAssistantMessageIndex]
                                _messages[currentAssistantMessageIndex] = current.copy(
                                    content = current.content.copy(
                                        content = "Error: ${event.message}"
                                    )
                                )
                            }
                            currentAssistantMessageId = null
                            currentAssistantMessageIndex = -1
                        }
                        is GenerationEvent.Metrics -> {
                            if (currentAssistantMessageIndex >= 0 &&
                                currentAssistantMessageIndex < _messages.size) {
                                val current = _messages[currentAssistantMessageIndex]
                                _messages[currentAssistantMessageIndex] =
                                    current.copy(decodingMetrics = event.metrics)
                            }
                        }
                        is GenerationEvent.ToolCall -> {}
                    }
                }
            } catch (e: Exception) {
                _isGenerating.value = false
                _error.value = e.message
                currentAssistantMessageId = null
                currentAssistantMessageIndex = -1
            }
        }
    }

    fun stop() {
        LlmModelWorker.ggufStopGeneration()
        _isGenerating.value = false
        currentAssistantMessageId = null
        currentAssistantMessageIndex = -1
    }

    fun clearMessages() {
        _messages.clear()
        currentAssistantMessageId = null
        currentAssistantMessageIndex = -1
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
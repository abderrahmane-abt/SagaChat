package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _messages = MutableStateFlow<List<Messages>>(emptyList())
    val messages: StateFlow<List<Messages>> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private var currentAssistantMessageId: String? = null

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
        _messages.value += userMessage

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
            _messages.value += assistantMessage

            try {
                LlmModelWorker.ggufGenerateStreaming(prompt, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            updateAssistantMessage { msg ->
                                msg.copy(
                                    content = msg.content.copy(
                                        content = msg.content.content + event.text
                                    )
                                )
                            }
                        }
                        is GenerationEvent.Done -> {
                            _isGenerating.value = false
                            currentAssistantMessageId = null
                        }
                        is GenerationEvent.Error -> {
                            _isGenerating.value = false
                            _error.value = event.message
                            updateAssistantMessage { msg ->
                                msg.copy(
                                    content = msg.content.copy(
                                        content = "Error: ${event.message}"
                                    )
                                )
                            }
                            currentAssistantMessageId = null
                        }
                        is GenerationEvent.Metrics -> {
                            updateAssistantMessage { msg ->
                                msg.copy(decodingMetrics = event.metrics)
                            }
                        }
                        is GenerationEvent.ToolCall -> {}
                    }
                }
            } catch (e: Exception) {
                _isGenerating.value = false
                _error.value = e.message
                currentAssistantMessageId = null
            }
        }
    }

    private fun updateAssistantMessage(transform: (Messages) -> Messages) {
        currentAssistantMessageId?.let { msgId ->
            _messages.value = _messages.value.map {
                if (it.msgId == msgId) transform(it) else it
            }
        }
    }

    fun stop() {
        LlmModelWorker.ggufStopGeneration()
        _isGenerating.value = false
        currentAssistantMessageId = null
    }

    fun clearMessages() {
        _messages.value = emptyList()
        currentAssistantMessageId = null
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }
}
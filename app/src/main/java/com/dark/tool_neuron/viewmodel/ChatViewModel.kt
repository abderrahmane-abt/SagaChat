package com.dark.tool_neuron.viewmodel

import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.messages.ContentType
import com.dark.tool_neuron.models.messages.MessageContent
import com.dark.tool_neuron.models.messages.Messages
import com.dark.tool_neuron.models.messages.Role
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
@HiltViewModel
class ChatViewModel  @Inject constructor(
    private val chatManager: ChatManager,
    private val generationManager: GenerationManager
) : ViewModel() {

    private val _messages = mutableStateListOf<Messages>()
    val messages: SnapshotStateList<Messages> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId

    // Streaming state
    private val _streamingUserMessage = MutableStateFlow<String?>(null)
    val streamingUserMessage: StateFlow<String?> = _streamingUserMessage

    private val _streamingAssistantMessage = MutableStateFlow("")
    val streamingAssistantMessage: StateFlow<String> = _streamingAssistantMessage

    // Track generation job for proper cancellation
    private var generationJob: Job? = null

    // Track current generation state for stop functionality
    private var currentUserMessage: Messages? = null
    private var currentGeneratedContent: String = ""
    private var currentMetrics: com.mp.ai_gguf.models.DecodingMetrics? = null

    fun loadChat(chatId: String) {
        viewModelScope.launch {
            _currentChatId.value = chatId
            chatManager.getChatMessages(chatId).onSuccess { loadedMessages ->
                _messages.clear()
                _messages.addAll(loadedMessages)
            }.onFailure { e ->
                _error.value = "Failed to load chat: ${e.message}"
            }
        }
    }

    fun sendMessage(prompt: String, maxTokens: Int = 512) {
        val chatId = _currentChatId.value
        if (chatId == null) {
            Log.d("ChatViewModel", "No chat selected")
            _error.value = "No chat selected"
            return
        }

        if (!generationManager.isModelLoaded()) {
            _error.value = "Please load a model first"
            return
        }

        if (_isGenerating.value) {
            return
        }

        // Set streaming user message
        _streamingUserMessage.value = prompt

        viewModelScope.launch {
            chatManager.addUserMessage(chatId, prompt).onSuccess { userMessage ->
                currentUserMessage = userMessage
                generate(chatId, userMessage, maxTokens)
            }.onFailure { e ->
                _error.value = "Failed to save message: ${e.message}"
                _streamingUserMessage.value = null
                currentUserMessage = null
            }
        }
    }

    private fun generate(chatId: String, userMessage: Messages, maxTokens: Int) {
        generationJob = viewModelScope.launch {
            _isGenerating.value = true
            _error.value = null
            _streamingAssistantMessage.value = ""
            currentGeneratedContent = ""
            currentMetrics = null

            // Batching variables
            var tokenBuffer = StringBuilder()
            var tokenCount = 0
            var lastUpdateTime = System.currentTimeMillis()
            val updateIntervalMs = 50L // Update UI every 50ms
            val tokenBatchSize = 3 // Or every 3 tokens (whichever comes first)

            try {
                val conversationPrompt = generationManager.buildConversationPrompt(
                    _messages,
                    userMessage.content.content
                )

                generationManager.generateStreaming(conversationPrompt, maxTokens).collect { event ->
                    when (event) {
                        is GenerationEvent.Token -> {
                            currentGeneratedContent += event.text
                            tokenBuffer.append(event.text)
                            tokenCount++

                            val currentTime = System.currentTimeMillis()
                            val shouldUpdate = tokenCount >= tokenBatchSize ||
                                    (currentTime - lastUpdateTime) >= updateIntervalMs

                            if (shouldUpdate) {
                                _streamingAssistantMessage.value = currentGeneratedContent
                                tokenBuffer.clear()
                                tokenCount = 0
                                lastUpdateTime = currentTime
                            }
                        }
                        is GenerationEvent.Done -> {
                            // Final update with complete content
                            _streamingAssistantMessage.value = currentGeneratedContent

                            _isGenerating.value = false

                            // Add both messages to the list
                            _messages.add(userMessage)

                            val assistantMessage = Messages(
                                role = Role.Assistant,
                                content = MessageContent(
                                    contentType = ContentType.Text,
                                    content = currentGeneratedContent
                                ),
                                decodingMetrics = currentMetrics
                            )
                            _messages.add(assistantMessage)

                            // Save assistant message
                            chatManager.addAssistantMessage(
                                chatId,
                                currentGeneratedContent,
                                currentMetrics
                            )

                            // Clear streaming state
                            _streamingUserMessage.value = null
                            _streamingAssistantMessage.value = ""
                            currentUserMessage = null
                            currentGeneratedContent = ""
                            currentMetrics = null
                        }
                        is GenerationEvent.Error -> {
                            // Final update before error
                            _streamingAssistantMessage.value = currentGeneratedContent

                            _isGenerating.value = false
                            _error.value = event.message

                            // Add user message and error message
                            _messages.add(userMessage)
                            val errorMessage = Messages(
                                role = Role.Assistant,
                                content = MessageContent(
                                    contentType = ContentType.Text,
                                    content = "Error: ${event.message}"
                                )
                            )
                            _messages.add(errorMessage)

                            // Clear streaming state
                            _streamingUserMessage.value = null
                            _streamingAssistantMessage.value = ""
                            currentUserMessage = null
                            currentGeneratedContent = ""
                            currentMetrics = null
                        }
                        is GenerationEvent.Metrics -> {
                            currentMetrics = event.metrics
                        }
                        is GenerationEvent.ToolCall -> {}
                    }
                }
            } catch (e: Exception) {
                _isGenerating.value = false
                _error.value = e.message

                // Save partial content if exists
                if (currentGeneratedContent.isNotEmpty() && currentUserMessage != null) {
                    _messages.add(currentUserMessage!!)
                    val partialMessage = Messages(
                        role = Role.Assistant,
                        content = MessageContent(
                            contentType = ContentType.Text,
                            content = "$currentGeneratedContent [incomplete]"
                        )
                    )
                    _messages.add(partialMessage)

                    viewModelScope.launch {
                        chatManager.addAssistantMessage(
                            chatId,
                            "$currentGeneratedContent [incomplete]",
                            null
                        )
                    }
                }

                _streamingUserMessage.value = null
                _streamingAssistantMessage.value = ""
                currentUserMessage = null
                currentGeneratedContent = ""
                currentMetrics = null
            }
        }
    }

    fun stop() {
        // Stop the generation
        generationManager.stopGeneration()

        // Cancel the coroutine job
        generationJob?.cancel()
        generationJob = null

        val chatId = _currentChatId.value

        // Save the partial conversation if there's content
        if (chatId != null &&
            currentUserMessage != null &&
            currentGeneratedContent.isNotEmpty()) {

            viewModelScope.launch {
                // Add user message to list
                _messages.add(currentUserMessage!!)

                // Add partial assistant message
                val assistantMessage = Messages(
                    role = Role.Assistant,
                    content = MessageContent(
                        contentType = ContentType.Text,
                        content = "$currentGeneratedContent [stopped]"
                    ),
                    decodingMetrics = currentMetrics
                )
                _messages.add(assistantMessage)

                // Save to database
                chatManager.addAssistantMessage(
                    chatId,
                    "$currentGeneratedContent [stopped]",
                    currentMetrics
                )
            }
        } else if (currentUserMessage != null) {
            // If no content generated yet, just add user message
            _messages.add(currentUserMessage!!)
        }

        // Clear all streaming state
        _isGenerating.value = false
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        currentUserMessage = null
        currentGeneratedContent = ""
        currentMetrics = null
    }

    fun clearMessages() {
        _messages.clear()
        _streamingUserMessage.value = null
        _streamingAssistantMessage.value = ""
        currentUserMessage = null
        currentGeneratedContent = ""
        currentMetrics = null
        _error.value = null
    }

    fun clearError() {
        _error.value = null
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch {
            chatManager.deleteMessage(messageId).onSuccess {
                _messages.removeIf { it.msgId == messageId }
            }.onFailure { e ->
                _error.value = "Failed to delete message: ${e.message}"
            }
        }
    }
}
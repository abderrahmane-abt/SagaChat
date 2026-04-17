package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.model.Chat
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models

    private val _actionWindowExpanded = MutableStateFlow(false)
    val actionWindowExpanded = _actionWindowExpanded.asStateFlow()

    private val _plusMenuExpanded = MutableStateFlow(false)
    val plusMenuExpanded = _plusMenuExpanded.asStateFlow()

    private val _webSearchEnabled = MutableStateFlow(false)
    val webSearchEnabled = _webSearchEnabled.asStateFlow()

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled = _thinkingEnabled.asStateFlow()

    private val _chatDocuments = MutableStateFlow<List<ChatDocument>>(emptyList())
    val chatDocuments: StateFlow<List<ChatDocument>> = _chatDocuments.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val _chats = MutableStateFlow<List<Chat>>(emptyList())
    val chats: StateFlow<List<Chat>> = _chats.asStateFlow()
    private val _loadModelWindow = MutableStateFlow(false)
    val loadModelWindows: StateFlow<Boolean> = _loadModelWindow.asStateFlow()

    fun toggleActionWindow() {
        _actionWindowExpanded.value = !_actionWindowExpanded.value
    }

    fun toggleLoadModelWindow() {
        _loadModelWindow.value = !_loadModelWindow.value
    }

    fun collapseActionWindow() {
        _actionWindowExpanded.value = false
    }

    fun togglePlusMenu() {
        _plusMenuExpanded.value = !_plusMenuExpanded.value
    }

    fun dismissPlusMenu() {
        _plusMenuExpanded.value = false
    }

    fun toggleWebSearch() {
        _webSearchEnabled.value = !_webSearchEnabled.value
    }

    fun toggleThinking() {
        _thinkingEnabled.value = !_thinkingEnabled.value
    }

    fun addDocument(document: ChatDocument) {
        _chatDocuments.value += document
    }

    fun removeDocument(docId: String) {
        _chatDocuments.value = _chatDocuments.value.filter { it.id != docId }
    }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
    }

    fun createNewChat() {
        _currentChatId.value = null
    }

    fun deleteChat(chatId: String) {
        _chats.value = _chats.value.filter { it.id != chatId }
        if (_currentChatId.value == chatId) _currentChatId.value = null
    }

    fun pinChat(chatId: String, pinned: Boolean) {
        _chats.value = _chats.value.map {
            if (it.id == chatId) it.copy(isPinned = pinned) else it
        }
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            val config = modelRepo.getConfig(model.id)
            val configJson = buildConfigJson(config)
            when (model.pathType) {
                PathType.FILE -> InferenceClient.loadModel(model.path, configJson)
                PathType.CONTENT_URI -> InferenceClient.loadModelFromUri(
                    context,
                    model.path.toUri(), configJson
                )
            }
            modelRepo.setActive(model.id)
        }
    }

    private fun buildConfigJson(config: ModelConfig?): String {
        if (config == null) return "{}"
        val sb = StringBuilder(256).append('{')
        val loading = config.loadingParamsJson
        val inference = config.inferenceParamsJson
        if (loading != "{}" && loading.isNotBlank()) {
            val inner = loading.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner).append(',')
        }
        if (inference != "{}" && inference.isNotBlank()) {
            val inner = inference.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner)
        }
        if (sb.last() == ',') sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }
}

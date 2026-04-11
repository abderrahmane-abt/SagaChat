package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.model.ChatDocument
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor() : ViewModel() {

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

    private val _chats = MutableStateFlow<List<com.dark.tool_neuron.model.Chat>>(emptyList())
    val chats: StateFlow<List<com.dark.tool_neuron.model.Chat>> = _chats.asStateFlow()

    fun toggleActionWindow() {
        _actionWindowExpanded.value = !_actionWindowExpanded.value
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
        _chatDocuments.value = _chatDocuments.value + document
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
}

package com.dark.tool_neuron.new_workers

import android.content.Context
import com.mp.user_data.BrainManager
import com.mp.user_data.models.ChatData
import com.mp.user_data.models.ChatInfo
import com.mp.user_data.models.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ChatWorker {

    private val _chatList = MutableStateFlow<List<ChatInfo>>(emptyList())
    val chatList: StateFlow<List<ChatInfo>> = _chatList.asStateFlow()
    private lateinit var brainManager: BrainManager
    private val _currentChatID = MutableStateFlow("")
    private val _currentChatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val loadedMessages: StateFlow<List<ChatMessage>> = _currentChatMessages.asStateFlow()


    fun init(context: Context){
        brainManager = BrainManager.getInstance(context)
        loadChats()
    }

    fun loadChats(){
        _chatList.value = brainManager.chatHelper.getAllChats()
    }

    fun newChat(title: String = "New-Chat", systemPrompt: String){
        _currentChatID.value = brainManager.chatHelper.createChat(title, systemPrompt)
    }

    fun loadChat(chatID: String): ChatData? {
        val chatData = brainManager.chatHelper.getChat(chatID)
        if (chatData != null) {
            _currentChatID.value = chatData.id
            _currentChatMessages.value = chatData.messages
        }
        return chatData
    }

    fun newMessage(message: ChatMessage) {
        brainManager.chatHelper.addMessage(_currentChatID.value, message)
    }

    fun updateMessage(message: ChatMessage) {
        brainManager.chatHelper.updateMessage(
            _currentChatID.value,
            message.id,
            message.chatMessageContent
        )
    }

    fun deleteMessage(messageID: String){
        brainManager.chatHelper.deleteMessage(_currentChatID.value, messageID)
    }

    fun deleteChat(chatID: String){
        brainManager.chatHelper.deleteChat(chatID)
    }

    fun saveChats(context: Context){
        brainManager.save(context)
    }
}
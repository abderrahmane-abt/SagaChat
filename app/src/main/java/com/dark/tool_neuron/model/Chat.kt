package com.dark.tool_neuron.model

data class Chat(
    val id: String,
    val title: String,
    val modelId: String,
    val modelName: String,
    val createdAt: Long,
    val updatedAt: Long,
    val messageCount: Int = 0,
    val isPinned: Boolean = false,
)

data class ChatMessage(
    val id: String,
    val chatId: String,
    val role: String,
    val content: String,
    val timestamp: Long,
    val tokenCount: Int = 0,
    val thinkingContent: String = "",
    val imageUris: List<String> = emptyList(),
)

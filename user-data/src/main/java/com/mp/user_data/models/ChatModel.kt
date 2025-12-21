package com.mp.user_data.models

import android.graphics.Bitmap
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Summary information about a chat.
 */
data class ChatInfo(
    val id: String = "",
    val title: String = "",
    val createdAt: Long = 0,
    val updatedAt: Long = 0,
    val messageCount: Int = 0
)

/**
 * Complete chat data including all messages.
 */
data class ChatData(
    val id: String = "",
    val title: String = "",
    val systemPrompt: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = 0,
    val updatedAt: Long = 0
)

@Serializable
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val timeStamp: Long = System.currentTimeMillis(),
    val input: String = "",
    val chatMessageType: ChatMessageType = ChatMessageType.NONE,
    val chatMessageContent: ChatMessageContent = ChatMessageContent.None()
)

@Serializable
enum class ChatMessageType {
    USER, LLM, NONE
}

@Serializable
enum class TaskType {
    TEXT, IMAGE, NONE
}

interface ChatMessageContent {
    @Serializable
    class None : ChatMessageContent

    @Serializable
    data class TextMessage(
        val text: String = ""
    ) : ChatMessageContent

    @Serializable
    data class ImageMessage(
        val imagePath: String = "",
        val currentStep: Int = 0,
        val totalSteps: Int = 0
    ) : ChatMessageContent

}

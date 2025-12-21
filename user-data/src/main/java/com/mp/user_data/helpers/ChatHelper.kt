package com.mp.user_data.helpers

import android.util.Log
import com.mp.user_data.models.ChatData
import com.mp.user_data.models.ChatInfo
import com.mp.user_data.models.ChatMessage
import com.mp.user_data.models.ChatMessageContent
import com.mp.user_data.models.ChatMessageType
import com.mp.user_data.ntds.neuron_tree.NeuronTree
import com.mp.user_data.ntds.neuron_tree.NodeType
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject

/**
 * Helper for managing chat conversations.
 * Each chat is stored as a node with messages in JSON format.
 *
 * Usage:
 * ```kotlin
 * val chatHelper = ChatHelper(tree)
 * val chatId = chatHelper.createChat("My Chat", "Welcome!")
 * chatHelper.addTextMessage(chatId, "Hello", ChatMessageType.USER)
 * chatHelper.addTextMessage(chatId, "Hi there!", ChatMessageType.LLM)
 * val messages = chatHelper.getMessages(chatId)
 * ```
 */
class ChatHelper(tree: NeuronTree) : BaseHelper(tree, "chatHistory") {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Create a new chat session.
     * @param title The chat title
     * @param systemPrompt Optional system prompt for the chat
     * @return The ID of the newly created chat
     */
    fun createChat(title: String, systemPrompt: String = ""): String {
        val chatData = JSONObject().apply {
            put("title", title)
            put("systemPrompt", systemPrompt)
            put("messages", JSONArray())
            put("createdAt", System.currentTimeMillis())
            put("updatedAt", System.currentTimeMillis())
        }

        val chatId = addChildNode(chatData.toString(), NodeType.LEAF)
        Log.d(TAG, "Created chat: $title (ID: $chatId)")
        return chatId
    }

    /**
     * Get all chat sessions.
     * @return List of ChatInfo objects
     */
    fun getAllChats(): List<ChatInfo> {
        return getAllChildren().mapNotNull { node ->
            parseNodeContent(node)?.let { jsonObj ->
                ChatInfo(
                    id = node.id,
                    title = jsonObj.optString("title", "Untitled"),
                    createdAt = jsonObj.optLong("createdAt", 0),
                    updatedAt = jsonObj.optLong("updatedAt", 0),
                    messageCount = jsonObj.optJSONArray("messages")?.length() ?: 0
                )
            }
        }
    }

    /**
     * Get a specific chat by ID.
     * @return ChatData if found, null otherwise
     */
    fun getChat(chatId: String): ChatData? {
        val node = getNodeById(chatId) ?: return null
        val jsonObj = parseNodeContent(node) ?: return null

        val messages = mutableListOf<ChatMessage>()
        val messagesArray = jsonObj.optJSONArray("messages")
        if (messagesArray != null) {
            for (i in 0 until messagesArray.length()) {
                try {
                    val msgString = messagesArray.getString(i)
                    val chatMessage = json.decodeFromString<ChatMessage>(msgString)
                    messages.add(chatMessage)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse message at index $i", e)
                }
            }
        }

        return ChatData(
            id = node.id,
            title = jsonObj.optString("title", "Untitled"),
            systemPrompt = jsonObj.optString("systemPrompt", ""),
            messages = messages,
            createdAt = jsonObj.optLong("createdAt", 0),
            updatedAt = jsonObj.optLong("updatedAt", 0)
        )
    }

    /**
     * Add a chat message to an existing chat.
     * @param chatId The chat ID
     * @param message The ChatMessage to add
     * @return true if successful, false if chat not found
     */
    fun addMessage(chatId: String, message: ChatMessage): Boolean {
        val node = getNodeById(chatId) ?: return false
        val jsonObj = parseNodeContent(node) ?: return false

        val messagesArray = jsonObj.optJSONArray("messages") ?: JSONArray()

        // Serialize ChatMessage to JSON string
        val messageJson = json.encodeToString(message)
        messagesArray.put(messageJson)

        jsonObj.put("messages", messagesArray)
        jsonObj.put("updatedAt", System.currentTimeMillis())

        return updateNodeContent(chatId, jsonObj.toString())
    }

    /**
     * Add a text message to an existing chat.
     * @param chatId The chat ID
     * @param text The message text
     * @param type The message type (USER or LLM)
     * @return true if successful, false if chat not found
     */
    fun addTextMessage(chatId: String, text: String, type: ChatMessageType): Boolean {
        val message = ChatMessage(
            chatMessageType = type, chatMessageContent = ChatMessageContent.TextMessage(text)
        )
        return addMessage(chatId, message)
    }

    /**
     * Add an image message to an existing chat.
     * @param chatId The chat ID
     * @param imagePath The path to the image
     * @param type The message type (USER or LLM)
     * @return true if successful, false if chat not found
     */
    fun addImageMessage(chatId: String, imagePath: String, currentStep: Int, totalSteps: Int, type: ChatMessageType): Boolean {
        val message = ChatMessage(
            chatMessageType = type, chatMessageContent = ChatMessageContent.ImageMessage(imagePath)
        )
        return addMessage(chatId, message)
    }

    /**
     * Get all messages from a chat.
     * @return List of messages, or empty list if chat not found
     */
    fun getMessages(chatId: String): List<ChatMessage> {
        return getChat(chatId)?.messages ?: emptyList()
    }

    /**
     * Update the title of a chat.
     * @return true if successful, false if chat not found
     */
    fun updateChatTitle(chatId: String, newTitle: String): Boolean {
        val node = getNodeById(chatId) ?: return false
        val jsonObj = parseNodeContent(node) ?: return false

        jsonObj.put("title", newTitle)
        jsonObj.put("updatedAt", System.currentTimeMillis())

        return updateNodeContent(chatId, jsonObj.toString())
    }

    /**
     * Delete a chat by ID.
     * @return true if successful, false if chat not found
     */
    fun deleteChat(chatId: String): Boolean {
        return deleteNode(chatId)
    }

    /**
     * Search for chats by title.
     * @param query The search query
     * @return List of matching ChatInfo objects
     */
    fun searchChats(query: String): List<ChatInfo> {
        val lowerQuery = query.lowercase()
        return getAllChats().filter {
            it.title.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Get the most recent chats.
     * @param limit Maximum number of chats to return
     * @return List of ChatInfo objects, sorted by update time (newest first)
     */
    fun getRecentChats(limit: Int = 10): List<ChatInfo> {
        return getAllChats().sortedByDescending { it.updatedAt }.take(limit)
    }

    /**
     * Clear all messages from a chat, keeping the chat itself.
     * @return true if successful, false if chat not found
     */
    fun clearMessages(chatId: String): Boolean {
        val node = getNodeById(chatId) ?: return false
        val jsonObj = parseNodeContent(node) ?: return false

        jsonObj.put("messages", JSONArray())
        jsonObj.put("updatedAt", System.currentTimeMillis())

        return updateNodeContent(chatId, jsonObj.toString())
    }

    /**
     * Get the last message from a chat.
     * @return The last ChatMessage, or null if chat is empty or not found
     */
    fun getLastMessage(chatId: String): ChatMessage? {
        val messages = getMessages(chatId)
        return messages.lastOrNull()
    }

    /**
     * Get message count for a chat.
     * @return The number of messages, or 0 if chat not found
     */
    fun getMessageCount(chatId: String): Int {
        return getMessages(chatId).size
    }

    /**
     * Delete a specific message by its ID.
     * @param chatId The chat ID
     * @param messageId The message ID to delete
     * @return true if successful, false if chat or message not found
     */
    fun deleteMessage(chatId: String, messageId: String): Boolean {
        val node = getNodeById(chatId) ?: return false
        val jsonObj = parseNodeContent(node) ?: return false

        val messagesArray = jsonObj.optJSONArray("messages") ?: return false
        val newMessagesArray = JSONArray()

        var found = false
        for (i in 0 until messagesArray.length()) {
            try {
                val msgString = messagesArray.getString(i)
                val chatMessage = json.decodeFromString<ChatMessage>(msgString)

                if (chatMessage.id != messageId) {
                    newMessagesArray.put(msgString)
                } else {
                    found = true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse message at index $i", e)
                // Keep the message if we can't parse it
                newMessagesArray.put(messagesArray.getString(i))
            }
        }

        if (!found) return false

        jsonObj.put("messages", newMessagesArray)
        jsonObj.put("updatedAt", System.currentTimeMillis())

        return updateNodeContent(chatId, jsonObj.toString())
    }

    /**
     * Update a specific message's content.
     * @param chatId The chat ID
     * @param messageId The message ID to update
     * @param newContent The new message content
     * @return true if successful, false if chat or message not found
     */
    fun updateMessage(chatId: String, messageId: String, newContent: ChatMessageContent): Boolean {
        val node = getNodeById(chatId) ?: return false
        val jsonObj = parseNodeContent(node) ?: return false

        val messagesArray = jsonObj.optJSONArray("messages") ?: return false
        val newMessagesArray = JSONArray()

        var found = false
        for (i in 0 until messagesArray.length()) {
            try {
                val msgString = messagesArray.getString(i)
                val chatMessage = json.decodeFromString<ChatMessage>(msgString)

                if (chatMessage.id == messageId) {
                    // Update the message
                    val updatedMessage = chatMessage.copy(chatMessageContent = newContent)
                    newMessagesArray.put(json.encodeToString(updatedMessage))
                    found = true
                } else {
                    newMessagesArray.put(msgString)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse message at index $i", e)
                newMessagesArray.put(messagesArray.getString(i))
            }
        }

        if (!found) return false

        jsonObj.put("messages", newMessagesArray)
        jsonObj.put("updatedAt", System.currentTimeMillis())

        return updateNodeContent(chatId, jsonObj.toString())
    }
}


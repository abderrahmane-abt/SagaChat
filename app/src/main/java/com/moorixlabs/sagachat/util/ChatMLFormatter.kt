package com.moorixlabs.sagachat.util

import com.moorixlabs.sagachat.model.ChatMessage

object ChatMLFormatter {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    /**
     * Formats a system prompt + message list into a ChatML string for Qwen-style models.
     * Includes the trailing empty assistant turn to force character continuation.
     */
    fun format(systemPrompt: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("$IM_START system\n$systemPrompt$IM_END\n")
        for (msg in messages) {
            val role = msg.role
            val content = msg.content.trim()
            if (content.isBlank()) continue
            sb.append("$IM_START $role\n$content$IM_END\n")
        }
        // Trailing empty assistant turn — forces the model to generate as the character.
        sb.append("$IM_START assistant\n")
        return sb.toString()
    }
}

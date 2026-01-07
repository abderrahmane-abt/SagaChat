package com.dark.tool_neuron.models.messages

import androidx.compose.runtime.Immutable
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.serialization.Serializable
import java.util.UUID

@Immutable
@Serializable
data class Messages(
    val msgId: String = UUID.randomUUID().toString(),
    val role: Role = Role.Assistant,
    val content: MessageContent = MessageContent(),
    val decodingMetrics: DecodingMetrics? = null
)

@Immutable
@Serializable
data class MessageContent(
    val contentType: ContentType = ContentType.None,
    val content: String = ""
)

@Serializable
enum class ContentType {
    None, Text, Image
}

@Serializable
enum class Role {
    User, Assistant
}
package com.mp.n_apps.agent

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ToolCall(
    val name: String,
    val params: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class ToolResult(
    val name: String,
    val success: Boolean,
    val data: JsonElement? = null,
    val error: String? = null
)

data class ToolLogEntry(
    val iteration: Int,
    val toolName: String,
    val params: String,
    val success: Boolean,
    val resultSummary: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class AgentLoopResult(
    val finalText: String,
    val toolLog: List<ToolLogEntry>,
    val totalIterations: Int,
    val success: Boolean,
    val error: String? = null
)

data class AgentProgress(
    val currentIteration: Int,
    val maxIterations: Int,
    val phase: String = "Working..."
)

package com.mp.ai_core.models

import kotlinx.coroutines.CompletableDeferred



data class GGUFModelLoadingParameters(
    val path: String,
    val threads: Int,
    val ctxSize: Int,
    val temp: Float,
    val topK: Int,
    val topP: Float,
    val minP: Float,
    val mirostat: Int,
    val mirostatTau: Float,
    val mirostatEta: Float,
    val seed: Int
)

data class GGUFModelTask(
    val input: String,
    val taskType: GGUFTaskType,
    val maxTokens: Int = 100,
    val toolJson: String = "",
    val events: GGUFStreamEvents,
    val result: CompletableDeferred<String>,
    val resultEmbedded: CompletableDeferred<FloatArray>
)

enum class GGUFTaskType {
    GENERATE, EMBEDDING
}

interface GGUFStreamEvents {
    fun onToken(token: String)
    fun onTool(toolName: String, toolArgs: String)
}
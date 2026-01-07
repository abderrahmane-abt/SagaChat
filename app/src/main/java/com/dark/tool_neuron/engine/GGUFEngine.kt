package com.dark.tool_neuron.engine

import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.models.StreamCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext

class GGUFEngine {
    private val nativeLib = GGUFNativeLib()
    private var isLoaded = false
    private var currentModelId: String? = null

    suspend fun load(model: Model, config: ModelConfig?): Boolean = withContext(Dispatchers.IO) {
        if (isLoaded) unload()

        val schema = GgufEngineSchema.fromJson(
            config?.modelLoadingParams,
            config?.modelInferenceParams
        )

        val loading = schema.loadingParams
        val inference = schema.inferenceParams

        val success = nativeLib.nativeLoadModel(
            path = model.modelPath,
            threads = loading.threads,
            ctxSize = loading.ctxSize,
            temp = inference.temperature,
            topK = inference.topK,
            topP = inference.topP,
            minP = inference.minP,
            mirostat = inference.mirostat,
            mirostatTau = inference.mirostatTau,
            mirostatEta = inference.mirostatEta,
            seed = inference.seed
        )

        if (success) {
            isLoaded = true
            currentModelId = model.id

            if (inference.systemPrompt.isNotEmpty()) {
                nativeLib.nativeSetSystemPrompt(inference.systemPrompt)
            }
            if (inference.chatTemplate.isNotEmpty()) {
                nativeLib.nativeSetChatTemplate(inference.chatTemplate)
            }
        }

        success
    }

    fun generateFlow(prompt: String, maxTokens: Int): Flow<GenerationEvent> = flow {
        if (!isLoaded) {
            emit(GenerationEvent.Error("Model not loaded"))
            return@flow
        }

        val channel = Channel<GenerationEvent>(Channel.UNLIMITED)

        val callback = object : StreamCallback {
            override fun onToken(token: String) {
                channel.trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, argsJson: String) {
                channel.trySend(GenerationEvent.ToolCall(name, argsJson))
            }

            override fun onDone() {
                channel.trySend(GenerationEvent.Done)
                channel.close()
            }

            override fun onError(message: String) {
                channel.trySend(GenerationEvent.Error(message))
                channel.close()
            }
        }

        withContext(Dispatchers.Default) {
            nativeLib.nativeGenerateStream(prompt, maxTokens, callback)
        }

        for (event in channel) {
            emit(event)
        }
    }

    fun stopGeneration() {
        nativeLib.nativeStopGeneration()
    }

    suspend fun unload() = withContext(Dispatchers.IO) {
        if (isLoaded) {
            nativeLib.nativeRelease()
            isLoaded = false
            currentModelId = null
        }
    }

    fun isModelLoaded(modelId: String): Boolean =
        isLoaded && currentModelId == modelId

    fun getModelInfo(): String? =
        if (isLoaded) nativeLib.nativeGetModelInfo() else null
}

sealed class GenerationEvent {
    data class Token(val text: String) : GenerationEvent()
    data class ToolCall(val name: String, val args: String) : GenerationEvent()
    data object Done : GenerationEvent()
    data class Error(val message: String) : GenerationEvent()
}
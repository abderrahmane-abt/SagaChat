package com.dark.tool_neuron.engine

import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.models.DecodingMetrics
import com.mp.ai_gguf.models.StreamCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
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

    /**
     * Generate tokens as a Flow using callbackFlow
     *
     * This properly handles the callback-to-flow conversion without
     * violating Flow invariants. The native callback runs on whatever
     * thread llama.cpp uses, and we safely send events to the channel.
     *
     * The flow is consumed on IO dispatcher and buffered for smooth streaming.
     */
    fun generateFlow(prompt: String, maxTokens: Int): Flow<GenerationEvent> = callbackFlow {
        if (!isLoaded) {
            trySend(GenerationEvent.Error("Model not loaded"))
            close()
            return@callbackFlow
        }

        val callback = object : StreamCallback {
            override fun onToken(token: String) {
                // trySend is thread-safe and non-blocking
                trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, argsJson: String) {
                trySend(GenerationEvent.ToolCall(name, argsJson))
            }

            override fun onDone() {
                trySend(GenerationEvent.Done)
                close() // Close the channel when done
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
                close()
            }

            override fun onMetrics(metrics: DecodingMetrics) {
                trySend(GenerationEvent.Metrics(metrics))
            }
        }

        // Run the native generation on IO thread
        // This call blocks until generation completes
        try {
            nativeLib.nativeGenerateStream(prompt, maxTokens, callback)
        } catch (e: Exception) {
            trySend(GenerationEvent.Error(e.message ?: "Generation failed"))
            close()
        }

        // Keep the flow alive until the channel is closed
        awaitClose {
            // Cleanup if the collector cancels
            // This will be called if the flow is cancelled
        }
    }
        .buffer(Channel.UNLIMITED) // Buffer to prevent backpressure blocking native code
        .flowOn(Dispatchers.IO)    // Ensure collection happens on IO dispatcher

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
    data class Metrics(val metrics: DecodingMetrics) : GenerationEvent()
}
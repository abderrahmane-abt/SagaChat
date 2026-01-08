package com.dark.tool_neuron.worker

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.service.IGgufGenerationCallback
import com.dark.tool_neuron.service.ILLMService
import com.dark.tool_neuron.service.IModelLoadCallback
import com.dark.tool_neuron.service.LLMService
import com.mp.ai_gguf.models.DecodingMetrics
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@SuppressLint("StaticFieldLeak")
object LlmModelWorker {

    private var service: ILLMService? = null
    private var boundContext: Context? = null
    private val serviceBound = CompletableDeferred<Unit>()

    var isModelLoaded = MutableStateFlow(false)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            service = ILLMService.Stub.asInterface(binder)
            serviceBound.complete(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
        }
    }

    fun bindService(context: Context) {
        val intent = Intent(context, LLMService::class.java)
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        boundContext = context.applicationContext
    }

    suspend fun loadGgufModel(model: Model, modelConfig: ModelConfig): Boolean {
        serviceBound.await()
        val svc = service ?: return false

        return suspendCancellableCoroutine { continuation ->
            val callback = object : IModelLoadCallback.Stub() {
                override fun onSuccess() {
                    isModelLoaded.value = true
                    continuation.resume(true)
                }

                override fun onError(message: String) {
                    isModelLoaded.value = false
                    continuation.resume(false)
                }
            }

            try {
                svc.loadGgufModel(
                    model.modelPath,
                    model.modelName,
                    modelConfig.modelLoadingParams ?: "",
                    modelConfig.modelInferenceParams ?: "",
                    callback
                )
            } catch (e: Exception) {
                continuation.resumeWithException(e)
            }
        }
    }

    /**
     * Generate tokens as a Flow using callbackFlow
     *
     * Uses callbackFlow to properly convert AIDL callbacks to a Flow
     * without violating Flow invariants.
     */
    fun ggufGenerateStreaming(prompt: String, maxToken: Int): Flow<GenerationEvent> = callbackFlow {
        // Wait for service to be bound
        serviceBound.await()

        val svc = service
        if (svc == null) {
            trySend(GenerationEvent.Error("Service not bound"))
            close()
            return@callbackFlow
        }

        val callback = object : IGgufGenerationCallback.Stub() {
            override fun onToken(token: String) {
                // trySend is thread-safe, works from any thread (including Binder thread)
                trySend(GenerationEvent.Token(token))
            }

            override fun onToolCall(name: String, args: String) {
                trySend(GenerationEvent.ToolCall(name, args))
            }

            override fun onMetrics(
                totalTokens: Int,
                promptTokens: Int,
                generatedTokens: Int,
                tokensPerSecond: Float,
                timeToFirstToken: Long,
                totalTimeMs: Long
            ) {
                trySend(
                    GenerationEvent.Metrics(
                        DecodingMetrics(
                            totalTokens,
                            promptTokens,
                            generatedTokens,
                            tokensPerSecond,
                            timeToFirstToken,
                            totalTimeMs
                        )
                    )
                )
            }

            override fun onDone() {
                trySend(GenerationEvent.Done)
                close() // Close the channel/flow when generation is complete
            }

            override fun onError(message: String) {
                trySend(GenerationEvent.Error(message))
                close()
            }
        }

        // Start generation (non-blocking, callbacks will be called asynchronously)
        try {
            svc.generateGguf(prompt, maxToken, callback)
        } catch (e: Exception) {
            trySend(GenerationEvent.Error(e.message ?: "Failed to start generation"))
            close()
        }

        // Keep the flow alive until closed by callbacks or cancelled by collector
        awaitClose {
            // Optional: stop generation if flow is cancelled
            // service?.stopGenerationGguf()
        }
    }.buffer(Channel.UNLIMITED) // Prevent backpressure from blocking Binder callbacks
        .flowOn(Dispatchers.IO)    // Collect on IO dispatcher

    fun ggufStopGeneration() {
        service?.stopGenerationGguf()
    }

    fun unloadGgufModel() {
        service?.unloadModelGguf()
        isModelLoaded.value = false
    }

    fun unbindService() {
        boundContext?.unbindService(connection)
        service = null
        boundContext = null
    }
}
package com.dark.ai_module.ai

import android.content.Context
import android.util.Log
import io.shubham0204.smollm.SmolLM
import io.shubham0204.smollm.SmolLM.InferenceParams
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Neuron {
    // ---------------------------------------------------------------------
    //  Internal data classes & sealed request type
    // ---------------------------------------------------------------------
    private data class Variant(
        val job: Job,
        val modelPath: File,
        val instance: SmolLM
    )

    private sealed interface Request {
        data class Blocking(
            val prompt: String,
            val completer: CompletableDeferred<String>? = null
        ) : Request

        data class Streaming(
            val prompt: String,
            val onToken: (String) -> Unit,
            val completer: CompletableDeferred<String>? = null
        ) : Request
    }

    // ---------------------------------------------------------------------
    //  State holders
    // ---------------------------------------------------------------------
    private var activeVariant: File? = null
    private val modelInstances = ConcurrentHashMap<String, Variant>()

    private val nvScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Emits true while a generation is running */
    val isGenerating = MutableStateFlow(false)

    /** Broadcasts every completed assistant reply */
    val replies = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Internal channel acting as a coroutine‑friendly queue */
    private val requestChannel = Channel<Request>(Channel.UNLIMITED)

    // ---------------------------------------------------------------------
    //  Public API — model management
    // ---------------------------------------------------------------------
    fun loadModel(
        path: File,
        context: Context,
        contextLength: Long = 8024,
        chatTemplate: String? = null,
        forceReload: Boolean = false,
        systemPrompt: String,
        onLoaded: (() -> Unit)? = null
    ) {
        require(path.exists()) { "Model file missing at: ${path.path}" }
        val modelId = path.absolutePath
        Log.d("Neuron", "Loading ${path.name}, size=${path.length()}")

        if (!forceReload && modelInstances.containsKey(modelId)) {
            activeVariant = path
            onLoaded?.invoke()
            return
        }

        unloadAllModels()
        val model = SmolLM(context)

        val job = nvScope.launch {
            runCatching {
                model.load(
                    path.path,
                    InferenceParams(
                        contextSize = contextLength,
                        chatTemplate = chatTemplate,
                        storeChats = false,
                        numThreads = maxOf(2, Runtime.getRuntime().availableProcessors() / 2),
                        useMmap = true,
                        useMlock = false
                    )
                )
                model.addSystemPrompt(systemPrompt)
                withContext(Dispatchers.Main) { onLoaded?.invoke() }
            }.onFailure {
                Log.e("Neuron", "Model load failed", it)
                model.close()
            }
        }

        modelInstances[modelId] = Variant(job, path, model)
        activeVariant = path
    }

    fun unloadActiveModel() {
        activeVariant?.let {
            modelInstances.remove(it.absolutePath)?.instance?.close()
        }
        activeVariant = null
        isGenerating.value = false
    }

    fun unloadAllModels() {
        modelInstances.values.forEach { it.instance.close() }
        modelInstances.clear()
        activeVariant = null
        isGenerating.value = false
    }

    fun stopGeneration(immediate: Boolean = false) {
        activeVariant?.let {
            modelInstances[it.absolutePath]?.instance?.let { model ->
                if (immediate) model.stopGenerationImmediately() else model.stopGeneration()
            }
        }
        isGenerating.value = false
    }

    fun listLoadedModels(): List<String> = modelInstances.keys.toList()

    // ---------------------------------------------------------------------
    //  Public API — enqueue generation requests
    // ---------------------------------------------------------------------

    /** Fire‑and‑forget; observe results on [replies] flow */
    suspend fun enqueuePrompt(prompt: String) {
        requestChannel.send(Request.Blocking(prompt))
    }

    /** Queue prompt and await full reply */
    suspend fun generateAndWait(prompt: String): String {
        val def = CompletableDeferred<String>()
        requestChannel.send(Request.Blocking(prompt, def))
        return def.await()
    }

    suspend fun generateStreamAndWait(prompt: String, onToken: (String) -> Unit): String {
        val def = CompletableDeferred<String>()
        requestChannel.send(Request.Streaming(prompt, onToken, def))
        return def.await()
    }

    /** Queue a streaming request. Caller receives tokens via [onToken] and the final reply as return value */
    suspend fun generateStreaming(
        prompt: String,
        onToken: (String) -> Unit
    ): String {
        val def = CompletableDeferred<String>()
        requestChannel.send(Request.Streaming(prompt, onToken, def))
        return def.await()
    }

    // ---------------------------------------------------------------------
    //  Processor — runs once; serialises all generation
    // ---------------------------------------------------------------------
    init {
        startRequestProcessor()
    }

    private fun startRequestProcessor() {
        nvScope.launch {
            requestChannel.consumeEach { req ->
                try {
                    isGenerating.value = true
                    when (req) {
                        is Request.Blocking -> {
                            val reply = generateBlockingInternal(req.prompt)
                            req.completer?.complete(reply)
                            replies.tryEmit(reply)
                        }
                        is Request.Streaming -> {
                            val reply = generateStreamingInternal(req.prompt, req.onToken)
                            req.completer?.complete(reply)
                            replies.tryEmit(reply)
                        }
                    }
                } catch (t: Throwable) {
                    Log.e("Neuron", "Generation error", t)
                    when (req) {
                        is Request.Blocking  -> req.completer?.completeExceptionally(t)
                        is Request.Streaming -> req.completer?.completeExceptionally(t)
                    }
                } finally {
                    isGenerating.value = false
                }
            }
        }
    }

    // ---------------------------------------------------------------------
    //  Internal helpers
    // ---------------------------------------------------------------------

    private suspend fun generateBlockingInternal(prompt: String): String {
        val model = getActiveModel()
        model.addUserMessage(prompt)
        val reply = withContext(Dispatchers.IO) { model.getResponse(prompt) }
        model.addAssistantMessage(reply)
        return reply.trim()
    }

    private suspend fun generateStreamingInternal(
        prompt: String,
        onToken: (String) -> Unit
    ): String {
        val model = getActiveModel()
        model.addUserMessage(prompt)
        val sb = StringBuilder()
        model.getResponseAsFlow(prompt).collect { token ->
            onToken(token)
            sb.append(token)
        }
        val reply = sb.toString().trim()
        model.addAssistantMessage(reply)
        return reply
    }

    private suspend fun getActiveModel(): SmolLM {
        val variant = activeVariant ?: error("No active model selected.")
        val entry = modelInstances[variant.absolutePath] ?: error("Model not loaded.")
        return entry.instance
    }
}

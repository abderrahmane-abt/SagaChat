package com.dark.tool_neuron.service.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import com.dark.tool_neuron.service.IGenerationCallback
import com.dark.tool_neuron.service.IInferenceService
import com.dark.tool_neuron.service.IModelLoadCallback
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

sealed class InferenceEvent {
    data class Token(val text: String) : InferenceEvent()
    data class ToolCall(val name: String, val argsJson: String) : InferenceEvent()
    data object Done : InferenceEvent()
    data class Error(val message: String) : InferenceEvent()
    data class Metrics(val metricsJson: String) : InferenceEvent()
    data class Progress(val progress: Float) : InferenceEvent()
}

sealed class ServiceState {
    data object Disconnected : ServiceState()
    data object Connecting : ServiceState()
    data object Connected : ServiceState()
    data class Crashed(val message: String) : ServiceState()
}

object InferenceClient {

    private const val TAG = "InferenceClient"
    private const val BIND_TIMEOUT_MS = 15_000L
    private const val REBIND_DELAY_MS = 1000L

    private val _service = MutableStateFlow<IInferenceService?>(null)
    private val _state = MutableStateFlow<ServiceState>(ServiceState.Disconnected)
    val state: StateFlow<ServiceState> = _state.asStateFlow()

    private val _isModelLoaded = MutableStateFlow(false)
    val isModelLoaded: StateFlow<Boolean> = _isModelLoaded.asStateFlow()

    private val _isTtsLoaded = MutableStateFlow(false)
    val isTtsLoaded: StateFlow<Boolean> = _isTtsLoaded.asStateFlow()

    private val _isSttLoaded = MutableStateFlow(false)
    val isSttLoaded: StateFlow<Boolean> = _isSttLoaded.asStateFlow()

    private val _isVlmLoaded = MutableStateFlow(false)
    val isVlmLoaded: StateFlow<Boolean> = _isVlmLoaded.asStateFlow()

    private val _lastCrashInfo = MutableStateFlow<CrashInfo?>(null)
    val lastCrashInfo: StateFlow<CrashInfo?> = _lastCrashInfo.asStateFlow()

    fun dismissCrashInfo() { _lastCrashInfo.value = null }

    private fun ingestCrashFromSvc(svc: IInferenceService) {
        try {
            val candidates = mutableListOf<CrashInfo>()
            CrashInfo.fromJson("gguf_lib", svc.drainCrashLogJson(), CrashInfo.Source.NATIVE_CRASH)
                ?.let { candidates += it }
            CrashInfo.fromJson("ai_sherpa", svc.drainSherpaCrashLogJson(), CrashInfo.Source.NATIVE_CRASH)
                ?.let { candidates += it }
            CrashInfo.fromJson("gguf_lib", svc.lastErrorJson ?: "", CrashInfo.Source.LIVE_ERROR)
                ?.let { candidates += it }
            CrashInfo.fromJson("ai_sherpa", svc.sherpaLastErrorJson ?: "", CrashInfo.Source.LIVE_ERROR)
                ?.let { candidates += it }
            val newest = candidates.maxByOrNull { it.timestamp }
            if (newest != null) _lastCrashInfo.value = newest
        } catch (_: Exception) {}
    }

    fun reportLiveError() {
        val svc = _service.value ?: return
        try {
            val candidates = mutableListOf<CrashInfo>()
            CrashInfo.fromJson("gguf_lib", svc.lastErrorJson ?: "", CrashInfo.Source.LIVE_ERROR)
                ?.let { candidates += it; svc.clearLastError() }
            CrashInfo.fromJson("ai_sherpa", svc.sherpaLastErrorJson ?: "", CrashInfo.Source.LIVE_ERROR)
                ?.let { candidates += it; svc.clearSherpaLastError() }
            val newest = candidates.maxByOrNull { it.timestamp }
            if (newest != null) _lastCrashInfo.value = newest
        } catch (_: Exception) {}
    }

    private var appContext: Context? = null
    @Volatile private var isBinding = false

    private val pendingLoads = mutableSetOf<CancellableContinuation<Result<String>>>()
    private val pendingLoadsLock = Any()

    private fun trackLoad(cont: CancellableContinuation<Result<String>>) {
        synchronized(pendingLoadsLock) { pendingLoads.add(cont) }
        cont.invokeOnCancellation {
            synchronized(pendingLoadsLock) { pendingLoads.remove(cont) }
        }
    }

    private fun untrackLoad(cont: CancellableContinuation<Result<String>>) {
        synchronized(pendingLoadsLock) { pendingLoads.remove(cont) }
    }

    private fun failPendingLoads(reason: String) {
        val toFail = synchronized(pendingLoadsLock) {
            val snapshot = pendingLoads.toList()
            pendingLoads.clear()
            snapshot
        }
        toFail.forEach { cont ->
            if (cont.isActive) cont.resume(Result.failure(RuntimeException(reason)))
        }
    }

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val svc = IInferenceService.Stub.asInterface(binder)
            _service.value = svc
            _state.value = ServiceState.Connected
            isBinding = false
            try { _isTtsLoaded.value = svc.isTtsLoaded } catch (_: Exception) {}
            try { _isSttLoaded.value = svc.isSttLoaded } catch (_: Exception) {}
            try { _isVlmLoaded.value = svc.isVlmLoaded } catch (_: Exception) {}
            ingestCrashFromSvc(svc)
            Log.d(TAG, "Service connected")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isVlmLoaded.value = false
            _state.value = ServiceState.Crashed("Service process died")
            isBinding = false
            failPendingLoads("Inference service crashed during model load")
            Log.w(TAG, "Service disconnected — will auto-rebind")
            rebind()
        }

        override fun onBindingDied(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _isTtsLoaded.value = false
            _isSttLoaded.value = false
            _isVlmLoaded.value = false
            _state.value = ServiceState.Crashed("Service binding died")
            isBinding = false
            failPendingLoads("Inference service binding died")
            Log.e(TAG, "Binding died — rebinding")
            rebind()
        }

        override fun onNullBinding(name: ComponentName?) {
            _state.value = ServiceState.Crashed("Service returned null binding")
            isBinding = false
        }
    }

    fun bind(context: Context) {
        appContext = context.applicationContext
        performBind()
    }

    fun unbind() {
        val ctx = appContext ?: return
        try { ctx.unbindService(connection) } catch (_: Exception) {}
        _service.value = null
        _state.value = ServiceState.Disconnected
        isBinding = false
    }

    private fun performBind() {
        val ctx = appContext ?: return
        if (isBinding) return
        synchronized(this) {
            if (isBinding) return
            isBinding = true
            _state.value = ServiceState.Connecting
            val intent = Intent(ctx, InferenceService::class.java)
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE or Context.BIND_IMPORTANT)
        }
    }

    private fun rebind() {
        val ctx = appContext ?: return
        try { ctx.unbindService(connection) } catch (_: Exception) {}
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            performBind()
        }, REBIND_DELAY_MS)
    }

    private suspend fun ensureBound(): IInferenceService =
        withTimeout(BIND_TIMEOUT_MS) { _service.first { it != null }!! }

    // ── Model lifecycle ──

    suspend fun loadModel(modelPath: String, configJson: String = "{}"): Result<String> {
        val svc = ensureBound()
        return suspendCancellableCoroutine { cont ->
            trackLoad(cont)
            svc.loadModel(modelPath, configJson, loadCallback(cont))
        }
    }

    suspend fun loadModelFromUri(context: Context, uri: Uri, configJson: String = "{}"): Result<String> {
        val svc = ensureBound()
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            ?: return Result.failure(IllegalArgumentException("Cannot open URI: $uri"))
        return suspendCancellableCoroutine { cont ->
            trackLoad(cont)
            cont.invokeOnCancellation { pfd.close() }
            svc.loadModelFromFd(pfd, configJson, object : IModelLoadCallback.Stub() {
                override fun onSuccess(modelInfoJson: String) {
                    pfd.close()
                    untrackLoad(cont)
                    _isModelLoaded.value = true
                    cont.resume(Result.success(modelInfoJson))
                }
                override fun onError(message: String) {
                    pfd.close()
                    untrackLoad(cont)
                    _isModelLoaded.value = false
                    reportLiveError()
                    cont.resume(Result.failure(RuntimeException(message)))
                }
            })
        }
    }

    suspend fun unloadModel() {
        _isModelLoaded.value = false
        try { ensureBound().unloadModel() } catch (_: Exception) {}
    }

    fun getModelInfo(): String? =
        try { _service.value?.getModelInfo() } catch (_: Exception) { null }

    // ── Generation ──

    fun generateMultiTurn(messagesJson: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = _service.first { it != null }!!
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.generateMultiTurn(messagesJson, maxTokens, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "Service error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    fun generate(prompt: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = _service.first { it != null }!!
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.generate(prompt, maxTokens, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "Service error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    fun stopGeneration() {
        try { _service.value?.stopGeneration() } catch (_: Exception) {}
    }

    // ── VLM ──

    suspend fun loadVlmProjectorFromUri(context: Context, uri: Uri, threads: Int = 2): Boolean =
        withContext(Dispatchers.IO) {
            val svc = ensureBound()
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return@withContext false
            try {
                val ok = svc.loadVlmProjectorFromFd(pfd, threads)
                _isVlmLoaded.value = ok
                ok
            } catch (e: Exception) {
                Log.e(TAG, "loadVlmProjectorFromUri failed", e)
                false
            } finally {
                try { pfd.close() } catch (_: Exception) {}
            }
        }

    suspend fun releaseVlmProjector() {
        _isVlmLoaded.value = false
        try { ensureBound().releaseVlmProjector() } catch (_: Exception) {}
    }

    fun getVlmDefaultMarker(): String =
        try { _service.value?.vlmDefaultMarker.orEmpty() } catch (_: Exception) { "" }

    fun getVlmInfo(): String? =
        try { _service.value?.getVlmInfo() } catch (_: Exception) { null }

    fun generateVlm(
        context: Context,
        messagesJson: String,
        imageUris: List<Uri>,
        maxTokens: Int,
    ): Flow<InferenceEvent> = callbackFlow {
        val svc = _service.first { it != null }!!
        val pfds: Array<ParcelFileDescriptor> = try {
            imageUris.map { uri ->
                context.contentResolver.openFileDescriptor(uri, "r")
                    ?: throw IllegalArgumentException("Cannot open image URI: $uri")
            }.toTypedArray()
        } catch (e: Exception) {
            trySend(InferenceEvent.Error(e.message ?: "Cannot open image URI"))
            close()
            return@callbackFlow
        }
        val cb = generationCallback { event -> trySend(event) }
        try {
            svc.generateVlm(messagesJson, pfds, maxTokens, cb)
        } catch (e: Exception) {
            trySend(InferenceEvent.Error(e.message ?: "Service error"))
            close()
        } finally {
            pfds.forEach { p -> try { p.close() } catch (_: Exception) {} }
        }
        awaitClose {}
    }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    // ── Sampling ──

    fun setSampling(samplingJson: String) {
        try { _service.value?.setSampling(samplingJson) } catch (_: Exception) {}
    }

    fun setSystemPrompt(prompt: String) {
        try { _service.value?.setSystemPrompt(prompt) } catch (_: Exception) {}
    }

    fun setChatTemplate(template: String) {
        try { _service.value?.setChatTemplate(template) } catch (_: Exception) {}
    }

    // ── Tool calling ──

    fun isToolCallingSupported(): Boolean =
        try { _service.value?.isToolCallingSupported() ?: false } catch (_: Exception) { false }

    fun setToolsJson(toolsJson: String) {
        try { _service.value?.setToolsJson(toolsJson) } catch (_: Exception) {}
    }

    fun enableToolCalling(toolsJson: String, grammarMode: Int = 1, useTypedGrammar: Boolean = true) {
        try { _service.value?.enableToolCalling(toolsJson, grammarMode, useTypedGrammar) } catch (_: Exception) {}
    }

    fun clearTools() {
        try { _service.value?.clearTools() } catch (_: Exception) {}
    }

    // ── Context ──

    fun getContextUsage(): Float =
        try { _service.value?.contextUsage ?: 0f } catch (_: Exception) { 0f }

    // ── Thinking ──

    fun supportsThinking(): Boolean =
        try { _service.value?.supportsThinking() ?: false } catch (_: Exception) { false }

    fun setThinkingEnabled(enabled: Boolean) {
        try { _service.value?.setThinkingEnabled(enabled) } catch (_: Exception) {}
    }

    // ── Optimizations ──

    fun setPromptCacheDir(path: String) {
        try { _service.value?.setPromptCacheDir(path) } catch (_: Exception) {}
    }

    fun warmUp(): Boolean =
        try { _service.value?.warmUp() ?: false } catch (_: Exception) { false }

    // ── TTS ──

    suspend fun loadTtsModel(configJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ensureBound().loadTtsModel(configJson)
            _isTtsLoaded.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "loadTtsModel failed", e)
            false
        }
    }

    suspend fun unloadTtsModel() {
        _isTtsLoaded.value = false
        try { ensureBound().unloadTtsModel() } catch (_: Exception) {}
    }

    suspend fun synthesize(text: String, speakerId: Int = 0, speed: Float = 1.0f): FloatArray? =
        withContext(Dispatchers.IO) {
            try { ensureBound().synthesize(text, speakerId, speed) } catch (e: Exception) {
                Log.e(TAG, "synthesize failed", e)
                null
            }
        }

    fun getTtsSampleRate(): Int =
        try { _service.value?.ttsSampleRate ?: 0 } catch (_: Exception) { 0 }

    // ── STT ──

    suspend fun loadSttModel(configJson: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val result = ensureBound().loadSttModel(configJson)
            _isSttLoaded.value = result
            result
        } catch (e: Exception) {
            Log.e(TAG, "loadSttModel failed", e)
            false
        }
    }

    suspend fun unloadSttModel() {
        _isSttLoaded.value = false
        try { ensureBound().unloadSttModel() } catch (_: Exception) {}
    }

    suspend fun recognize(samples: FloatArray, sampleRate: Int = 16000): String? =
        withContext(Dispatchers.IO) {
            try { ensureBound().recognize(samples, sampleRate) } catch (e: Exception) {
                Log.e(TAG, "recognize failed", e)
                null
            }
        }

    // ── Internal helpers ──

    private fun loadCallback(cont: CancellableContinuation<Result<String>>) =
        object : IModelLoadCallback.Stub() {
            override fun onSuccess(modelInfoJson: String) {
                untrackLoad(cont)
                _isModelLoaded.value = true
                if (cont.isActive) cont.resume(Result.success(modelInfoJson))
            }
            override fun onError(message: String) {
                untrackLoad(cont)
                _isModelLoaded.value = false
                reportLiveError()
                if (cont.isActive) cont.resume(Result.failure(RuntimeException(message)))
            }
        }

    private fun generationCallback(emit: (InferenceEvent) -> Unit) =
        object : IGenerationCallback.Stub() {
            override fun onToken(token: String) { emit(InferenceEvent.Token(token)) }
            override fun onToolCall(name: String, argsJson: String) { emit(InferenceEvent.ToolCall(name, argsJson)) }
            override fun onDone() { emit(InferenceEvent.Done) }
            override fun onError(message: String) {
                emit(InferenceEvent.Error(message))
                reportLiveError()
            }
            override fun onMetrics(metricsJson: String) { emit(InferenceEvent.Metrics(metricsJson)) }
            override fun onProgress(progress: Float) { emit(InferenceEvent.Progress(progress)) }
        }
}

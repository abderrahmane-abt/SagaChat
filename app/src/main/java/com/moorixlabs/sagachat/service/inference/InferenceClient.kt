package com.moorixlabs.sagachat.service.inference

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.Log
import com.dark.tn_security.TnCode
import com.dark.tn_security.TnEvent
import com.dark.tn_security.TnLevel
import com.dark.tn_security.TnModule
import com.dark.tn_security.TnSecurity
import com.dark.tn_security.TnStage
import com.moorixlabs.sagachat.service.IGenerationCallback
import com.moorixlabs.sagachat.service.IInferenceService
import com.moorixlabs.sagachat.service.IModelLoadCallback
import com.moorixlabs.sagachat.service.ITnEventCallback
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
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

    // Set when the user taps Stop on the foreground notification. The next
    // onServiceDisconnected / onBindingDied must NOT auto-rebind, otherwise
    // BIND_AUTO_CREATE makes Android respawn :inference the moment we killed
    // it. Consumed on the next disconnect callback.
    @Volatile private var userStopRequested: Boolean = false

    private val _contextUsage = MutableStateFlow(0f)
    val contextUsage: StateFlow<Float> = _contextUsage.asStateFlow()

    private val _lastCrashInfo = MutableStateFlow<CrashInfo?>(null)
    val lastCrashInfo: StateFlow<CrashInfo?> = _lastCrashInfo.asStateFlow()

    fun dismissCrashInfo() { _lastCrashInfo.value = null }

    /**
     * Service-side TnEvent stream subscriber. Events arriving here are
     * forwarded into the app-process TnSecurity (so HxsSink can persist them
     * and the UI can observe them via TnSecurity.events) and, for backward
     * compatibility with the existing crash dialog, populate _lastCrashInfo
     * for error-level + crash events.
     */
    private val tnCallback = object : ITnEventCallback.Stub() {
        override fun onEvent(
            kind: Int, level: Int, module: Int, code: Int, stage: Int,
            tag: String?, opId: String?, file: String?, line: Int, func: String?,
            message: String?, suggestion: String?, timestampMs: Long, tid: Int,
        ) {
            val tnMod = TnModule.fromInt(module)
            val ev: TnEvent = when (kind) {
                1 -> TnEvent.Error(
                    timestampMs = timestampMs, module = tnMod, opId = opId, tid = tid,
                    code = TnCode.fromInt(code), stage = TnStage.fromInt(stage),
                    file = file, line = line, func = func,
                    message = message ?: "", suggestion = suggestion,
                )
                2 -> TnEvent.Cancellation(
                    timestampMs = timestampMs, module = tnMod, opId = opId, tid = tid,
                    reason = message,
                )
                else -> TnEvent.Log(
                    timestampMs = timestampMs, module = tnMod, opId = opId, tid = tid,
                    level = TnLevel.fromInt(level), tag = tag,
                    file = file, line = line, func = func,
                    message = message ?: "",
                )
            }

            // Forward into app-side TnSecurity so all sinks (HxsSink, etc.) see it.
            forwardToAppSinks(ev)

            // Back-compat: surface errors + crashes in the existing crash dialog.
            if (ev is TnEvent.Error) {
                _lastCrashInfo.value = CrashInfo.fromTnEvent(ev)
            }
        }

        override fun onCrashReplay(crashJson: String?, crashFilePath: String?) {
            val info = CrashInfo.fromCrashReplay(crashJson ?: "{}", crashFilePath ?: "")
            if (info != null) _lastCrashInfo.value = info
        }
    }

    private fun forwardToAppSinks(ev: TnEvent) {
        // Re-emit through app-side TnSecurity. Since the app process runs its
        // own TnSecurity (with its own sink list — HxsSink, LogcatSink, etc.),
        // we just call the emission helpers which fan out to those sinks.
        when (ev) {
            is TnEvent.Log -> TnSecurity.log(
                level = ev.level, module = ev.module, message = ev.message,
                tag = ev.tag, opId = ev.opId, file = ev.file, line = ev.line, func = ev.func,
            )
            is TnEvent.Error -> TnSecurity.error(
                code = ev.code, stage = ev.stage, module = ev.module, message = ev.message,
                suggestion = ev.suggestion, opId = ev.opId, file = ev.file, line = ev.line, func = ev.func,
            )
            is TnEvent.Cancellation -> TnSecurity.cancel(
                module = ev.module, opId = ev.opId, reason = ev.reason,
            )
            is TnEvent.Crash -> { /* delivered separately via onCrashReplay */ }
        }
    }


    /** Kept as a no-op for now — events now arrive automatically via [tnCallback]. */
    @Suppress("unused")
    fun reportLiveError() {
        // Intentionally empty. Pre-tn_security callers polled the service for
        // an opaque last-error JSON; with tn_security every error is pushed
        // through tnCallback the moment it's emitted.
    }

    private var appContext: Context? = null
    @Volatile private var isBinding = false

    private val pendingLoads = mutableSetOf<CancellableContinuation<Result<String>>>()
    private val pendingLoadsLock = Any()

    private fun trackLoad(
        cont: CancellableContinuation<Result<String>>,
        onCancel: (() -> Unit)? = null,
    ) {
        synchronized(pendingLoadsLock) { pendingLoads.add(cont) }
        // CancellableContinuation accepts exactly one cancellation handler — additional
        // resources (e.g. the pfd in loadModelFromUri) must be closed here, not via
        // a second invokeOnCancellation call (which throws IllegalStateException).
        cont.invokeOnCancellation {
            try { onCancel?.invoke() } catch (_: Throwable) {}
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
            // Always sync the four "is loaded" flags from the freshly-bound
            // service. Without the isModelLoaded probe here, a low-memory
            // service kill that didn't fire onServiceDisconnected in time
            // would leave the pill stuck on "Model Loaded" while the new
            // :inference process actually has nothing loaded.
            val modelLoadedOnSvc = try { svc.isModelLoaded } catch (_: Exception) { false }
            if (_isModelLoaded.value != modelLoadedOnSvc) {
                Log.i(TAG, "rebind: model state drift, client=${_isModelLoaded.value} svc=$modelLoadedOnSvc — taking svc as truth")
            }
            _isModelLoaded.value = modelLoadedOnSvc
            try {
                svc.registerTnEvents(tnCallback)
                svc.replayPendingCrashes()
            } catch (e: Throwable) {
                Log.e(TAG, "tn_security stream registration failed", e)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _state.value = if (userStopRequested) ServiceState.Disconnected else ServiceState.Crashed("Service process died")
            isBinding = false
            failPendingLoads("Inference service crashed during model load")
            if (userStopRequested) {
                userStopRequested = false
                Log.i(TAG, "Service disconnected — user-initiated stop, not rebinding")
                return
            }
            Log.w(TAG, "Service disconnected — will auto-rebind")
            rebind()
        }

        override fun onBindingDied(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _state.value = if (userStopRequested) ServiceState.Disconnected else ServiceState.Crashed("Service binding died")
            isBinding = false
            failPendingLoads("Inference service binding died")
            if (userStopRequested) {
                userStopRequested = false
                Log.i(TAG, "Binding died — user-initiated stop, not rebinding")
                return
            }
            Log.e(TAG, "Binding died — rebinding")
            rebind()
        }

        override fun onNullBinding(name: ComponentName?) {
            _service.value = null
            _isModelLoaded.value = false
            _state.value = ServiceState.Crashed("Service returned null binding")
            isBinding = false
            failPendingLoads("Inference service returned null binding")
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
        // Match the disconnect callbacks: unbinding the service means nothing
        // is loaded from the client's perspective anymore.
        _isModelLoaded.value = false
        isBinding = false
    }

    /**
     * Entry point from the foreground notification's Stop button. Tears the
     * binding down first so BIND_AUTO_CREATE can't respawn :inference the
     * moment the service kills itself, then forwards ACTION_STOP to the
     * service so it can run its unload-everything teardown before exit.
     */
    fun requestUserStop(context: Context) {
        appContext = context.applicationContext
        userStopRequested = true
        unbind()
        val intent = Intent(context, InferenceService::class.java).setAction(InferenceService.ACTION_STOP)
        try { context.startService(intent) } catch (_: Exception) {}
    }

    private fun performBind() {
        val ctx = appContext ?: return
        if (isBinding) return
        synchronized(this) {
            if (isBinding) return
            isBinding = true
            _state.value = ServiceState.Connecting
            val intent = Intent(ctx, InferenceService::class.java)
            ctx.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun rebind() {
        val ctx = appContext ?: return
        try { ctx.unbindService(connection) } catch (_: Exception) {}
        Handler(Looper.getMainLooper()).postDelayed({
            performBind()
        }, REBIND_DELAY_MS)
    }

    private suspend fun ensureBound(): IInferenceService =
        withTimeout(BIND_TIMEOUT_MS) { _service.first { it != null }!! }

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
            trackLoad(cont) { pfd.close() }
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

    fun generateMultiTurn(messagesJson: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
            if (svc == null) {
                trySend(InferenceEvent.Error("Inference service unavailable"))
                close()
                return@callbackFlow
            }
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
            val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
            if (svc == null) {
                trySend(InferenceEvent.Error("Inference service unavailable"))
                close()
                return@callbackFlow
            }
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

    /**
     * Drives GGMLEngine.compact on the :inference side and forwards summary
     * tokens / metrics / done events through the same channel as generate.
     * Caller is expected to replace the persisted chat history with the
     * collected summary after [InferenceEvent.Done].
     */
    fun compactConversation(messagesJson: String, maxTokens: Int): Flow<InferenceEvent> =
        callbackFlow {
            val svc = withTimeoutOrNull(BIND_TIMEOUT_MS) { _service.first { it != null } }
            if (svc == null) {
                trySend(InferenceEvent.Error("Inference service unavailable"))
                close()
                return@callbackFlow
            }
            val cb = generationCallback { event -> trySend(event) }
            try {
                svc.compactConversation(messagesJson, maxTokens, cb)
            } catch (e: Exception) {
                trySend(InferenceEvent.Error(e.message ?: "Service error"))
                close()
            }
            awaitClose {}
        }.buffer(Channel.UNLIMITED).flowOn(Dispatchers.IO)

    fun setSampling(samplingJson: String) {
        try { _service.value?.setSampling(samplingJson) } catch (_: Exception) {}
    }

    fun setSystemPrompt(prompt: String) {
        try { _service.value?.setSystemPrompt(prompt) } catch (_: Exception) {}
    }

    fun setChatTemplate(template: String) {
        try { _service.value?.setChatTemplate(template) } catch (_: Exception) {}
    }

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

    fun getContextUsage(): Float =
        try { _service.value?.contextUsage ?: 0f } catch (_: Exception) { 0f }

    fun setThreadMode(mode: Int) {
        try { _service.value?.setThreadMode(mode) } catch (_: Exception) {}
    }

    fun getMemoryStatsJson(): String? =
        try { _service.value?.memoryStatsJson } catch (_: Exception) { null }

    fun getVtCacheStatsJson(): String? =
        try { _service.value?.vtCacheStatsJson } catch (_: Exception) { null }

    fun supportsThinking(): Boolean =
        try { _service.value?.supportsThinking() ?: false } catch (_: Exception) { false }

    fun setThinkingEnabled(enabled: Boolean) {
        try { _service.value?.setThinkingEnabled(enabled) } catch (_: Exception) {}
    }

    fun setPromptCacheDir(path: String) {
        try { _service.value?.setPromptCacheDir(path) } catch (_: Exception) {}
    }

    fun warmUp(): Boolean =
        try { _service.value?.warmUp() ?: false } catch (_: Exception) { false }

    private suspend inline fun <T> streamBytesToService(
        bytes: ByteArray,
        crossinline call: (ParcelFileDescriptor, Long) -> T,
    ): T? {
        if (bytes.isEmpty()) return null
        val pipe = try { ParcelFileDescriptor.createPipe() }
        catch (e: Throwable) { Log.e(TAG, "createPipe failed", e); return null }
        val readEnd = pipe[0]
        val writeEnd = pipe[1]
        return kotlinx.coroutines.coroutineScope {
            val writer = launch(Dispatchers.IO) {
                try {
                    FileOutputStream(writeEnd.fileDescriptor).use { it.write(bytes) }
                } catch (e: Throwable) {
                    Log.e(TAG, "pipe write failed", e)
                } finally {
                    try { writeEnd.close() } catch (_: Throwable) {}
                }
            }
            try {
                call(readEnd, bytes.size.toLong())
            } catch (e: Throwable) {
                Log.e(TAG, "service call over pipe failed", e); null
            } finally {
                try { readEnd.close() } catch (_: Throwable) {}
                writer.join()
            }
        }
    }

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
            override fun onToken(token: String) {
                emit(InferenceEvent.Token(token))
                _contextUsage.value = getContextUsage()
            }
            override fun onToolCall(name: String, argsJson: String) { emit(InferenceEvent.ToolCall(name, argsJson)) }
            override fun onDone() {
                emit(InferenceEvent.Done)
                _contextUsage.value = getContextUsage()
            }
            override fun onError(message: String) {
                emit(InferenceEvent.Error(message))
                reportLiveError()
            }
            override fun onMetrics(metricsJson: String) { emit(InferenceEvent.Metrics(metricsJson)) }
            override fun onProgress(progress: Float) {
                emit(InferenceEvent.Progress(progress))
                _contextUsage.value = getContextUsage()
            }
        }
}

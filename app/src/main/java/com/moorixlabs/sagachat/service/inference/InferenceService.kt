package com.moorixlabs.sagachat.service.inference

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.DeadObjectException
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.Process
import android.util.Log
import android.graphics.Bitmap
import com.dark.gguf_lib.GGMLEngine
import com.dark.gguf_lib.models.GenerationEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import org.json.JSONArray
import com.dark.tn_security.LogcatSink
import com.dark.tn_security.TnCode
import com.dark.tn_security.TnEvent
import com.dark.tn_security.TnModule
import com.dark.tn_security.TnSecurity
import com.dark.tn_security.TnSink
import com.dark.tn_security.TnStage
import java.io.File
import android.os.RemoteCallbackList
import com.moorixlabs.sagachat.R
import com.moorixlabs.sagachat.service.IGenerationCallback
import com.moorixlabs.sagachat.service.IInferenceService
import com.moorixlabs.sagachat.service.IModelLoadCallback
import com.moorixlabs.sagachat.service.ITnEventCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder

class InferenceService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = GGMLEngine()

    private val crashDir: File by lazy {
        File(filesDir, "tn_security/crashes").also { it.mkdirs() }
    }

    private val tnClients = RemoteCallbackList<ITnEventCallback>()

    private val tnSink = TnSink { ev ->
        val n = tnClients.beginBroadcast()
        try {
            for (i in 0 until n) {
                val cb = tnClients.getBroadcastItem(i)
                try {
                    when (ev) {
                        is TnEvent.Log -> cb.onEvent(
                            0, ev.level.value, ev.module.value, 0, 0,
                            ev.tag, ev.opId, ev.file, ev.line, ev.func,
                            ev.message, null, ev.timestampMs, ev.tid,
                        )
                        is TnEvent.Error -> cb.onEvent(
                            1, 4 /*ERROR*/, ev.module.value, ev.code.value, ev.stage.value,
                            null, ev.opId, ev.file, ev.line, ev.func,
                            ev.message, ev.suggestion, ev.timestampMs, ev.tid,
                        )
                        is TnEvent.Cancellation -> cb.onEvent(
                            2, 2 /*INFO*/, ev.module.value, TnCode.CANCELLED.value, 0,
                            null, ev.opId, null, 0, null,
                            ev.reason ?: "", null, ev.timestampMs, ev.tid,
                        )
                        is TnEvent.Crash -> cb.onCrashReplay(
                            // Re-emit as a synthetic crash JSON for the app side.
                            // Real crash files are shipped via replayPendingCrashes().
                            "{}", ev.crashFilePath,
                        )
                    }
                } catch (_: DeadObjectException) {
                    // Will be reaped by RemoteCallbackList automatically.
                } catch (_: Throwable) {
                    // Per-callback failure must not kill the broadcast loop.
                }
            }
        } finally {
            tnClients.finishBroadcast()
        }
    }



    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        setupNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Inference ready"))
        engine.setTokenBatchSize(STREAMING_TOKEN_BATCH_BYTES)
        try {
            TnSecurity.init(
                context = this,
                module = TnModule.TN_SERVICE,
                crashDir = crashDir,
                installSignalHandlers = true,
            )
            TnSecurity.addSink(LogcatSink())  // dev visibility
            TnSecurity.addSink(tnSink)        // broadcast to bound clients
        } catch (t: Throwable) {
            Log.e(TAG, "tn_security init failed", t)
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            // Hard-stop from the foreground notification: cancel any in-flight
            // work AND tear down every loaded backend before we let the
            // process go down, so memory is reclaimed immediately instead of
            // waiting on onDestroy ordering / lingering binder refs.
            unloadEverything()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            killSelfProcess()
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        unloadEverything()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
        killSelfProcess()
    }

    /**
     * After unloadEverything+stopSelf, bound clients can still be holding the
     * binder alive (foreground service + active binding outlive stopSelf).
     * Killing the process is the only way to (a) force onServiceDisconnected
     * on every client — which is what flips InferenceClient._isModelLoaded
     * back to false so the pill stops lying — and (b) actually free the model
     * pages, since the app process re-binds will spawn a fresh :inference.
     */
    private fun killSelfProcess() {
        Process.killProcess(Process.myPid())
    }

    override fun onDestroy() {
        unloadEverything()
        scope.cancel()
        super.onDestroy()
    }

    @Volatile private var teardownInProgress = false

    /**
     * Synchronously release every backend held by this service: cancel any
     * generation, free the LLM + VLM projector, close TTS/STT/RAG, and tear
     * down the SD pipeline. Idempotent — safe to call from onStartCommand,
     * onTaskRemoved, and onDestroy (which often fire in sequence).
     */
    private fun unloadEverything() {
        if (teardownInProgress) return
        teardownInProgress = true
        try { engine.stopGeneration() } catch (_: Throwable) {}
        try {
            runBlocking(Dispatchers.IO) {
                if (engine.isLoaded) engine.unload()
            }
        } catch (_: Throwable) {}
    }

    private val binder = object : IInferenceService.Stub() {

        override fun loadModel(modelPath: String, configJson: String, callback: IModelLoadCallback) {
            scope.launch {
                try {
                    logDeviceProfile()
                    val modelSize = File(modelPath).length()
                    val memAvail = readMemAvailableBytes()
                    Log.i(TAG, "loadModel path=$modelPath sizeMb=${modelSize / (1024 * 1024)} memAvailMb=${memAvail / (1024 * 1024)}")
                    if (memAvail in 1 until (modelSize * 6 / 5)) {
                        val msg = "Not enough free memory: model needs ~${modelSize / (1024 * 1024)} MB, " +
                            "device has ~${memAvail / (1024 * 1024)} MB free. Close other apps or pick a smaller quant."
                        captureKt("loadModel", "path=$modelPath", "InsufficientRam", msg)
                        safeCallback(callback) { it.onError(msg) }
                        return@launch
                    }

                    if (engine.isLoaded) engine.unload()
                    val config = parseConfig(configJson)
                    // Stash the user's thread-mode preference BEFORE load so the
                    // single apply_thread_mode that runs during ctx construction
                    // picks the right pool. Without this, load would build the
                    // pool for the default mode (1) and a post-load setThreadMode
                    // would tear it down and rebuild — wasting ~50 ms per load.
                    engine.setThreadMode(config.optInt("threadMode", 1))
                    val success = engine.load(
                        path = modelPath,
                        contextSize = config.optInt("contextSize", 4096),
                        flashAttn = config.optBoolean("flashAttn", true),
                        cacheTypeK = config.optString("cacheTypeK", "q8_0"),
                        cacheTypeV = config.optString("cacheTypeV", "q8_0"),
                    )
                    if (success) {
                        applySamplingFromConfig(config)
                        updateNotification("Model loaded")
                        safeCallback(callback) { it.onSuccess(engine.getModelInfoJson() ?: "{}") }
                    } else {
                        val msg = "Model load returned false"
                        captureKt("loadModel", "path=$modelPath", "ModelLoad", msg)
                        safeCallback(callback) { it.onError(msg) }
                    }
                } catch (e: OutOfMemoryError) {
                    try { engine.unload() } catch (_: Throwable) {}
                    captureKt("loadModel", "path=$modelPath", "OOM",
                        "JVM out of memory while loading the model. Try a smaller quant or lower Context Size.")
                    safeCallback(callback) { it.onError("Out of memory") }
                } catch (e: Exception) {
                    captureKt("loadModel", "path=$modelPath", "ModelLoad", e.message ?: e.javaClass.simpleName)
                    safeCallback(callback) { it.onError(e.message ?: "Unknown error") }
                }
            }
        }

        override fun loadModelFromFd(pfd: ParcelFileDescriptor, configJson: String, callback: IModelLoadCallback) {
            val fd = pfd.detachFd()
            scope.launch {
                try {
                    if (engine.isLoaded) engine.unload()
                    val config = parseConfig(configJson)
                    // See loadModel above — pre-set thread mode to avoid the
                    // post-load threadpool rebuild.
                    engine.setThreadMode(config.optInt("threadMode", 1))
                    val success = engine.loadFromFd(
                        fd = fd,
                        contextSize = config.optInt("contextSize", 4096),
                        flashAttn = config.optBoolean("flashAttn", true),
                        cacheTypeK = config.optString("cacheTypeK", "q8_0"),
                        cacheTypeV = config.optString("cacheTypeV", "q8_0"),
                    )
                    if (success) {
                        applySamplingFromConfig(config)
                        updateNotification("Model loaded")
                        safeCallback(callback) { it.onSuccess(engine.getModelInfoJson() ?: "{}") }
                    } else {
                        safeCallback(callback) { it.onError("Failed to load model from FD") }
                    }
                } catch (e: OutOfMemoryError) {
                    try { engine.unload() } catch (_: Throwable) {}
                    safeCallback(callback) { it.onError("Out of memory") }
                } catch (e: Exception) {
                    safeCallback(callback) { it.onError(e.message ?: "Unknown error") }
                }
            }
        }

        override fun unloadModel() {
            scope.launch {
                if (engine.isLoaded) engine.unload()
                updateNotification("Inference ready")
            }
        }

        override fun isModelLoaded(): Boolean = engine.isLoaded

        override fun getModelInfo(): String? = engine.getModelInfoJson()

        override fun generate(prompt: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateFlow(prompt, maxTokens), callback)
        }

        override fun generateMultiTurn(messagesJson: String, maxTokens: Int, callback: IGenerationCallback) {
            collectFlow(engine.generateMultiTurnFlow(messagesJson, maxTokens), callback)
        }

        override fun compactConversation(messagesJson: String, maxTokens: Int, callback: IGenerationCallback) {
            // Append the summarize instruction as a synthetic user turn, run
            // the normal multi-turn generate, then wipe the KV cache so the
            // next real turn prefills from scratch against whatever new
            // history the caller installs (typically [system, assistant:
            // summary, user: …]).
            val augmented = try {
                val arr = org.json.JSONArray(messagesJson)
                arr.put(org.json.JSONObject().apply {
                    put("role", "user")
                    put("content", GGMLEngine.DEFAULT_SUMMARIZE_PROMPT)
                })
                arr.toString()
            } catch (e: Exception) {
                try { callback.onError("invalid messages json: ${e.message}") } catch (_: Exception) {}
                return
            }
            collectFlow(
                flow = engine.generateMultiTurnFlow(augmented, maxTokens),
                callback = callback,
                onComplete = { try { engine.resetKvCache() } catch (_: Throwable) {} },
            )
        }

        override fun stopGeneration() {
            engine.stopGeneration()
        }

        override fun setSampling(samplingJson: String) {
            engine.updateSamplerParams(samplingJson)
        }

        override fun setSystemPrompt(prompt: String) {
            engine.setSystemPrompt(prompt)
        }

        override fun setChatTemplate(template: String) {
            engine.setChatTemplate(template)
        }

        override fun isToolCallingSupported(): Boolean = false

        override fun setToolsJson(toolsJson: String) {}

        override fun enableToolCalling(toolsJson: String, grammarMode: Int, useTypedGrammar: Boolean) {}

        override fun clearTools() {}

        override fun getContextUsage(): Float = engine.getContextUsage()

        override fun getContextInfo(prompt: String?): String? = null

        override fun getMemoryStatsJson(): String? = engine.getMemoryStatsJson()

        override fun getVtCacheStatsJson(): String = engine.vtCacheStatsJson()

        override fun supportsThinking(): Boolean =
            engine.isLoaded && engine.supportsThinking()

        override fun setThinkingEnabled(enabled: Boolean) {
            engine.setThinkingEnabled(enabled)
        }

        override fun setPromptCacheDir(path: String) {
            engine.setPromptCacheDir(path)
        }

        override fun warmUp(): Boolean = engine.warmUp()

        override fun setThreadMode(mode: Int) {
            engine.setThreadMode(mode)
        }

        override fun getStateSize(): Long = engine.getStateSize()

        override fun stateSaveToFile(path: String): Boolean = engine.stateSaveToFile(path)

        override fun stateLoadFromFile(path: String): Boolean = engine.stateLoadFromFile(path)

        override fun registerTnEvents(cb: ITnEventCallback?) {
            if (cb != null) tnClients.register(cb)
        }

        override fun unregisterTnEvents(cb: ITnEventCallback?) {
            if (cb != null) tnClients.unregister(cb)
        }

        override fun replayPendingCrashes() {
            try {
                val replayed = TnSecurity.drainCrashFiles(crashDir)
                for (crash in replayed) {
                    val json = try {
                        // Re-read the file would be cleaner but drain already
                        // deleted it. Build a minimal envelope from the
                        // already-materialised TnEvent.Crash so the client
                        // can render it consistently.
                        JSONObject().apply {
                            put("signal", crash.signal)
                            put("signal_name", crash.signalName)
                            put("timestamp_ms", crash.timestampMs)
                            put("pid", crash.pid)
                            put("tid", crash.tid)
                            put("module", crash.module.value)
                            put("module_slug", crash.module.slug)
                            put("fault_addr", crash.faultAddr ?: "")
                        }.toString()
                    } catch (_: Throwable) { "{}" }
                    val n = tnClients.beginBroadcast()
                    try {
                        for (i in 0 until n) {
                            try {
                                tnClients.getBroadcastItem(i)
                                    .onCrashReplay(json, crash.crashFilePath)
                            } catch (_: Throwable) {}
                        }
                    } finally { tnClients.finishBroadcast() }
                }
            } catch (e: Throwable) {
                Log.e(TAG, "replayPendingCrashes failed", e)
            }
        }

    }

    private fun collectFlow(
        flow: Flow<GenerationEvent>,
        callback: IGenerationCallback,
        onComplete: () -> Unit = {},
    ) {
        scope.launch(Dispatchers.IO) {
            try {
                flow.collect { event ->
                    try {
                        when (event) {
                            is GenerationEvent.Token -> callback.onToken(event.text)
                            is GenerationEvent.Done -> callback.onDone()
                            is GenerationEvent.Error -> callback.onError(event.message)
                            is GenerationEvent.Metrics -> {
                                val m = event.metrics
                                val json = JSONObject().apply {
                                    put("tokensPerSecond", m.tokensPerSecond)
                                    put("timeToFirstTokenMs", m.timeToFirstTokenMs)
                                    put("totalTimeMs", m.totalTimeMs)
                                    put("tokensEvaluated", m.tokensEvaluated)
                                    put("tokensPredicted", m.tokensPredicted)
                                    put("modelSizeMB", m.modelSizeMB)
                                    put("contextSizeMB", m.contextSizeMB)
                                    put("peakMemoryMB", m.peakMemoryMB)
                                    put("memoryUsagePercent", m.memoryUsagePercent)
                                }
                                callback.onMetrics(json.toString())
                            }
                            is GenerationEvent.Progress -> callback.onProgress(event.progress)
                            is GenerationEvent.VlmStageMetrics -> Unit
                            is GenerationEvent.VlmKvCacheStatus -> Unit
                            is GenerationEvent.VtCacheStatus -> Unit
                        }
                    } catch (_: DeadObjectException) {
                        engine.stopGeneration()
                        return@collect
                    }
                }
            } catch (_: DeadObjectException) {
                engine.stopGeneration()
            } catch (e: Exception) {
                try { callback.onError(e.message ?: "Unknown error") } catch (_: Exception) {}
            } finally {
                try { onComplete() } catch (_: Throwable) {}
            }
        }
    }

    private fun parseConfig(json: String): JSONObject =
        if (json.isBlank()) JSONObject() else try { JSONObject(json) } catch (_: Exception) { JSONObject() }

    private fun applySamplingFromConfig(config: JSONObject) {
        if (config.has("temperature") || config.has("topK") || config.has("topP")) {
            engine.setSampling(
                temperature = config.optDouble("temperature", 0.7).toFloat(),
                topK = config.optInt("topK", 40),
                topP = config.optDouble("topP", 0.9).toFloat(),
                minP = config.optDouble("minP", 0.05).toFloat(),
                mirostat = config.optInt("mirostat", 0),
                mirostatTau = config.optDouble("mirostatTau", 5.0).toFloat(),
                mirostatEta = config.optDouble("mirostatEta", 0.1).toFloat(),
                seed = config.optInt("seed", -1),
            )
        }

        val advanced = JSONObject()
        for (key in ADVANCED_SAMPLING_KEYS) {
            if (config.has(key)) advanced.put(key, config.get(key))
        }
        if (advanced.length() > 0) engine.updateSamplerParams(advanced.toString())

        val nWindow = config.optInt("kvWindow", 0)
        if (nWindow > 0) {
            engine.setKvPolicy(
                nSink = config.optInt("kvSink", 4),
                nWindow = nWindow,
                evictAtFull = config.optBoolean("kvEvictAtFull", true),
            )
        }

        val system = config.optString("systemPrompt", "")
        if (system.isNotEmpty()) engine.setSystemPrompt(system)
    }

    private inline fun <T> safeCallback(target: T, block: (T) -> Unit) {
        try { block(target) } catch (_: DeadObjectException) {}
    }

    /**
     * Compat shim — translates the old captureKt() signature into a structured
     * TnSecurity.error call. Keeps existing call sites compiling while every
     * error flows through the unified event pipeline.
     */
    private fun captureKt(op: String, detail: String?, category: String, message: String) {
        val (code, stage) = mapCategoryToTn(category)
        TnSecurity.withOp(op) {
            TnSecurity.error(
                code = code,
                stage = stage,
                module = TnModule.TN_SERVICE,
                message = if (detail.isNullOrBlank()) message else "$message (detail: $detail)",
            )
        }
    }

    private fun mapCategoryToTn(category: String): Pair<TnCode, TnStage> = when (category) {
        "InsufficientRam" -> TnCode.OOM             to TnStage.INIT
        "OOM"             -> TnCode.OOM             to TnStage.LOAD
        "ModelLoad"       -> TnCode.MODEL_LOAD_FAIL to TnStage.LOAD
        "TTS"             -> TnCode.DECODE_FAIL     to TnStage.TTS_GENERATE
        "STT"             -> TnCode.DECODE_FAIL     to TnStage.STT_DECODE
        "VLM"             -> TnCode.DECODE_FAIL     to TnStage.VLM_DECODE_IMG
        "RAG"             -> TnCode.DECODE_FAIL     to TnStage.RAG_INGEST
        "SD"              -> TnCode.DECODE_FAIL     to TnStage.SD_UNET
        else              -> TnCode.UNKNOWN         to TnStage.UNSPECIFIED
    }

    private fun readAllFromPfd(pfd: ParcelFileDescriptor, hintedSize: Long): ByteArray {
        val capacity = if (hintedSize in 1..(64L * 1024L * 1024L)) hintedSize.toInt() else 0
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
            if (capacity > 0) {
                val buf = ByteArray(capacity)
                var read = 0
                while (read < capacity) {
                    val n = input.read(buf, read, capacity - read)
                    if (n <= 0) break
                    read += n
                }
                if (read == capacity) buf else buf.copyOf(read)
            } else {
                input.readBytes()
            }
        }
    }

    private fun serializeHits(hits: List<com.dark.gguf_lib.models.RAGResult>): String {
        val arr = JSONArray()
        for (h in hits) {
            arr.put(
                JSONObject()
                    .put("text", h.text)
                    .put("doc_id", h.docId)
                    .put("chunk_index", h.chunkIndex)
                    .put("score", h.score.toDouble()),
            )
        }
        return arr.toString()
    }

    private fun setupNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Inference Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply { setShowBadge(false) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        // Route Stop through :app's BroadcastReceiver, NOT directly into this
        // service. The receiver unbinds the InferenceClient first so the
        // BIND_AUTO_CREATE binding can't respawn :inference the instant we
        // kill ourselves, then forwards ACTION_STOP back here for teardown.
        val stopPi = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, InferenceStopReceiver::class.java).setAction(InferenceStopReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.inference_leaf)
            .setContentTitle("Tool Neuron")
            .setContentText(text)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop", stopPi).build())
            .build()
    }

    private fun updateNotification(text: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(text))
    }

    @Volatile private var deviceProfileLogged = false

    private fun logDeviceProfile() {
        if (deviceProfileLogged) return
        deviceProfileLogged = true
        try {
            val abis = Build.SUPPORTED_ABIS.joinToString(",")
            val features = readCpuFeatures()
            val totalMb = readMemTotalBytes() / (1024 * 1024)
            Log.i(TAG, "device profile: model=${Build.MODEL} soc=${Build.SOC_MODEL} abis=$abis ramMb=$totalMb features=$features")
        } catch (t: Throwable) {
            Log.w(TAG, "logDeviceProfile failed: ${t.message}")
        }
    }

    private fun readCpuFeatures(): String {
        return try {
            File("/proc/cpuinfo").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith("Features") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?: ""
            }
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readMemAvailableBytes(): Long = readMemKey("MemAvailable:")

    private fun readMemTotalBytes(): Long = readMemKey("MemTotal:")

    private fun readMemKey(prefix: String): Long {
        return try {
            File("/proc/meminfo").bufferedReader().useLines { lines ->
                lines.firstOrNull { it.startsWith(prefix) }
                    ?.let { line ->
                        val kb = line.substringAfter(':').trim().substringBefore(' ').toLongOrNull()
                        if (kb != null) kb * 1024L else -1L
                    } ?: -1L
            }
        } catch (_: Throwable) {
            -1L
        }
    }

    companion object {
        const val ACTION_STOP = "com.moorixlabs.sagachat.inference.ACTION_STOP"

        private const val TAG = "InferenceService"
        private const val CHANNEL_ID = "tn_inference"
        private const val NOTIFICATION_ID = 0x544E4953 // TNIS
        private const val STREAMING_TOKEN_BATCH_BYTES = 8
        private const val VT_CACHE_BUDGET_BYTES = 500L * 1024L * 1024L

        private val ADVANCED_SAMPLING_KEYS = arrayOf(
            "repeatPenalty", "frequencyPenalty", "presencePenalty", "penaltyLastN",
            "dryMultiplier", "dryBase", "dryAllowedLength", "dryPenaltyLastN",
            "xtcProbability", "xtcThreshold",
        )
    }
}

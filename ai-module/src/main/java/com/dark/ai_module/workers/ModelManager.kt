package com.dark.ai_module.workers

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Process
import android.util.Log
import com.dark.ai_module.db.DatabaseProvider
import com.dark.ai_module.db.ModelDAO
import com.dark.ai_module.model.*
import com.mp.ai_core.services.GenerationService
import com.mp.ai_core.services.IGenerationCallback
import com.mp.ai_core.services.IGenerationService
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds

/**
 * ModelManager – combines:
 *   • DB helpers (observe/add/remove models)
 *   • AIDL bridge to the GenerationService
 *   • High‑level streaming API (generateStreaming, stopGeneration, …)
 */
object ModelManager {

    private const val TAG = "ModelManager"

    /* ----------------------------------------------------------------- */
    /*  Service / AIDL communication                                   */
    /* ----------------------------------------------------------------- */
    private var service: IGenerationService? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            binder?.let {
                service = IGenerationService.Stub.asInterface(it)
                Log.i(TAG, "GenerationService connected")
            }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            Log.w(TAG, "GenerationService disconnected")
        }
    }

    /* ----------------------------------------------------------------- */
    /*  DB / DAO state                                                  */
    /* ----------------------------------------------------------------- */
    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: ModelDAO

    /** Call once (e.g. in Application.onCreate()) */
    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context).ModelDAO()
            Intent(context, GenerationService::class.java).also { intent ->
                context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            }
        }
    }

    /** Make sure init() has been called before DAO usage. */
    private fun ensureDaoInitialized() {
        check(initGuard.get()) { "ModelManager.init(context) must be called before use." }
    }

    /* ----------------------------------------------------------------- */
    /*  Public state exposed to the UI                                 */
    /* ----------------------------------------------------------------- */
    private val _currentModel = MutableStateFlow(getDefaultModelData())
    val currentModel: StateFlow<ModelsData> = _currentModel.asStateFlow()

    private val _params = MutableStateFlow(ParamsBundle())
    val params: StateFlow<ParamsBundle> = _params.asStateFlow()

    /** true while a generation request is in flight */
    val isGenerating = MutableStateFlow(false)

    /** Emitted whenever a full reply is completed */
    val replies = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /* ----------------------------------------------------------------- */
    /*  Loading / unloading a model via the service                    */
    /* ----------------------------------------------------------------- */
    suspend fun loadModelAwait(
        modelData: ModelsData,
        defaults: ManagerDefaults = ManagerDefaults(),
        chatTemplate: String? = null,
        keepInMemory: Boolean = false,
        onLoaded: (LoadState) -> Unit
    ): Result<Unit> = withContext(Dispatchers.IO) {
        onLoaded(LoadState.Idle)
        onLoaded(LoadState.Loading(0f))
        try {
            val f = File(modelData.modelPath)
            if (!f.exists()) {
                val err = IllegalArgumentException("Missing model: ${f.absolutePath}")
                onLoaded(LoadState.Error(err.message ?: "Load failed"))
                return@withContext Result.failure(err)
            }

            val init = ModelInitParams(
                threads = defaults.contextLength / 128,
                gpuLayers = 0,
                useMMAP = true,
                useMLOCK = keepInMemory,
                ctxSize = defaults.contextLength,
                temp = defaults.contextLength.toFloat() / 100.0f,
                topK = 20,
                topP = 0.5f,
                minP = 0.0f,
                systemPrompt = defaults.systemPrompt,
                chatTemplate = chatTemplate ?: modelData.chatTemplate
            )

            val svc = service ?: run {
                onLoaded(LoadState.Error("Service not bound"))
                return@withContext Result.failure(RuntimeException("Service not bound"))
            }

            val ok = svc.loadModel(
                modelData.modelPath,
                init.threads,
                init.gpuLayers,
                init.useMMAP,
                init.ctxSize,
                init.temp,
                init.topK,
                init.topP,
                init.minP
            )

            if (!ok) {
                onLoaded(LoadState.Error("Model load failed in service"))
                return@withContext Result.failure(RuntimeException("Model load failed in service"))
            }

            svc.setSystemPrompt(init.systemPrompt)
            init.chatTemplate?.let { svc.setChatTemplate(it) }

            _currentModel.value = modelData
            onLoaded(LoadState.OnLoaded(modelData))
            Result.success(Unit)
        } catch (t: Throwable) {
            onLoaded(LoadState.Error(t.message ?: "Load failed"))
            Result.failure(t)
        }
    }

    fun unloadModel() {
        val svc = service ?: return
        svc.unloadModel()
        _currentModel.value = getDefaultModelData()
    }

    /* ----------------------------------------------------------------- */
    /*  Streaming API – works over the AIDL binding                       */
    /* ----------------------------------------------------------------- */
    suspend fun generateStreaming(
        prompt: String,
        gen: GenerationParams = GenerationParams(),
        toolJson: String? = null,
        onToolCalled: (String, String) -> Unit = { _, _ -> },
        onToken: (String) -> Unit = {}
    ): String {
        val def = CompletableDeferred<String>()

        val normalized = ToolJsonUtils.normalizeSpec(toolJson)
        val deduped = ToolJsonUtils.maybeDedup(normalized)

        queue.send(
            Request.Streaming(
                prompt = prompt,
                gen = gen,
                onToken = onToken,
                toolJson = deduped,
                onToolCalled = onToolCalled,
                completer = def
            )
        )

        return def.await()
    }

    fun stopGeneration() {
        val svc = service ?: return
        svc.stopGeneration()
        isGenerating.value = false
    }

    fun setSystemPrompt(prompt: String) {
        service?.setSystemPrompt(prompt)
    }

    /* ----------------------------------------------------------------- */
    /*  Graceful shutdown – closes queue and releases the service      */
    /* ----------------------------------------------------------------- */
    fun shutdown() {
        queue.close()
        processorJob?.cancel()
        stopGeneration()
        unloadModel()
        genDispatcher.cancel()
        genExecutor.shutdown()
        Log.i(TAG, "ModelManager shut down")
    }

    /* ----------------------------------------------------------------- */
    /*  In‑memory request queue & processor (serialises calls)         */
    /* ----------------------------------------------------------------- */
    private sealed interface Request {
        data class Blocking(
            val prompt: String,
            val gen: GenerationParams,
            val completer: CompletableDeferred<String>
        ) : Request

        data class Streaming(
            val prompt: String,
            val gen: GenerationParams,
            val onToken: (String) -> Unit,
            val toolJson: String?,
            val onToolCalled: (String, String) -> Unit,
            val completer: CompletableDeferred<String>
        ) : Request
    }

    private val queue = Channel<Request>(capacity = 64)
    private var processorJob: Job? = null

    /* ------------------------------------------------------------ */
    /*  Coroutine scope used **only** for the background queue    */
    /* ------------------------------------------------------------ */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        startProcessor()
    }

    private fun startProcessor() {
        processorJob?.cancel()
        processorJob = scope.launch {
            Log.d(TAG, "Processor started on ${Thread.currentThread().name}")
            queue.consumeEach { req ->
                try {
                    isGenerating.value = true
                    when (req) {
                        is Request.Blocking -> handleBlocking(req)
                        is Request.Streaming -> handleStreaming(req)
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Generation error", t)
                } finally {
                    isGenerating.value = false
                }
            }
        }
    }

    /* ---- handle requests ---- */
    private suspend fun handleBlocking(r: Request.Blocking) {
        val svc = service ?: run {
            r.completer.completeExceptionally(RuntimeException("Service not bound"))
            return
        }

        svc.generate(
            r.prompt,
            r.gen.maxTokens,
            object : IGenerationCallback.Stub() {
                var acc = StringBuilder()
                override fun onToken(token: String) {
                    acc.append(token)
                }

                override fun onToolCall(name: String, args: String) { /* no-op */ }
                override fun onDone() {
                    r.completer.complete(acc.toString())
                }

                override fun onError(error: String) {
                    r.completer.completeExceptionally(RuntimeException(error))
                }
            }
        )
    }

    private fun handleStreaming(r: Request.Streaming) {
        val svc = service ?: run {
            r.completer.completeExceptionally(RuntimeException("Service not bound"))
            return
        }

        svc.generate(
            r.prompt,
            r.gen.maxTokens,
            object : IGenerationCallback.Stub() {
                var acc = StringBuilder()
                override fun onToken(token: String) {
                    r.onToken(token)
                    acc.append(token)
                }

                override fun onToolCall(name: String, args: String) {
                    r.onToolCalled(name, args)
                }

                override fun onDone() {
                    r.completer.complete(acc.toString())
                }

                override fun onError(error: String) {
                    r.completer.completeExceptionally(RuntimeException(error))
                }
            }
        )
    }

    /* ----------------------------------------------------------------- */
    /*  Tool‑JSON sanitisers – identical to the original logic         */
    /* ----------------------------------------------------------------- */
    private object ToolJsonUtils {
        private const val MAX_DESC = 512
        private const val MAX_SPEC_BYTES = 64 * 1024
        private var lastHash: Int? = null
        private const val DEDUP = true

        fun normalizeSpec(spec: String?): String? {
            if (spec.isNullOrBlank()) return null
            val root: JSONArray = try {
                when (spec.trimStart().firstOrNull()) {
                    '[' -> JSONArray(spec)
                    '{' -> JSONArray().put(JSONObject(spec))
                    else -> return null
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Invalid tool spec JSON", t)
                return null
            }

            for (i in 0 until root.length()) {
                val item = root.optJSONObject(i) ?: continue
                val func = item.optJSONObject("function") ?: continue

                func.optString("description")
                    .takeIf { it.length > MAX_DESC }?.let {
                        func.put("description", it.take(MAX_DESC))
                    }

                val params = func.optJSONObject("parameters") ?: continue
                val required = params.opt("required")
                if (required != null && required !is JSONArray) {
                    val arr = JSONArray()
                    when (required) {
                        is String -> arr.put(required)
                        is Iterable<*> -> required.forEach { k ->
                            if (k is String) arr.put(k)
                        }
                    }
                    params.put("required", arr)
                }

                val props = params.optJSONObject("properties") ?: continue
                val ordered = JSONObject()
                props.keys().asSequence().sorted().forEach { key ->
                    ordered.put(key, props.get(key))
                }
                params.put("properties", ordered)
            }

            val txt = root.toString()
            if (txt.toByteArray().size > MAX_SPEC_BYTES) {
                Log.w(TAG, "Tool spec too big – dropping descriptions")
                for (i in 0 until root.length()) {
                    root.optJSONObject(i)
                        ?.optJSONObject("function")
                        ?.remove("description")
                }
                return root.toString()
            }
            return txt
        }

        fun maybeDedup(spec: String?): String? {
            if (!DEDUP || spec == null) return spec
            val h = spec.hashCode()
            return if (lastHash == h) null else {
                lastHash = h
                spec
            }
        }

        fun isSchemaEcho(argsJson: String?): Boolean {
            if (argsJson.isNullOrBlank()) return false
            return try {
                val obj = JSONObject(argsJson)
                val calls = obj.optJSONArray("tool_calls") ?: return false
                val first = calls.optJSONObject(0) ?: return false
                val a = first.optJSONObject("arguments") ?: return false
                a.has("type") || a.has("properties") || a.has("required")
            } catch (_: Throwable) {
                false
            }
        }
    }

    /* ----------------------------------------------------------------- */
    /*  Background dispatcher/executor for the queue processor            */
    /* ----------------------------------------------------------------- */
    private val genExecutor = Executors.newSingleThreadExecutor { r ->
        Thread({
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
            r.run()
        }, "LLM-Gen")
    }

    private val genDispatcher: CoroutineDispatcher =
        genExecutor.asCoroutineDispatcher()

    /* ----------------------------------------------------------------- */
    /*  DB‑API – user‑callable helpers                                 */
    /* ----------------------------------------------------------------- */
    fun observeModels(): Flow<List<ModelsData>> {
        ensureDaoInitialized()
        return dao.getAllModels()
    }

    suspend fun addModel(model: ModelsData) =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.insertModel(model)
        }

    suspend fun removeModel(modelName: String) =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.getModelByName(modelName)?.let { dao.deleteModel(it) }
        }

    suspend fun checkIfInstalled(modelName: String): Boolean =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.getModelByName(modelName) != null
        }

    suspend fun getFirstModel(): ModelsData? =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.getAllModels().firstOrNull()?.firstOrNull()
        }

    suspend fun getModel(modelName: String): ModelsData? =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.getModelByName(modelName)
        }

    suspend fun isAnyModelInstalled(): Boolean =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.getAllModels().firstOrNull()?.isNotEmpty() == true
        }

    suspend fun getAllModels(): List<ModelsData> =
        withContext(Dispatchers.IO) {
            ensureDaoInitialized()
            dao.getAllModels().firstOrNull() ?: emptyList()
        }
}
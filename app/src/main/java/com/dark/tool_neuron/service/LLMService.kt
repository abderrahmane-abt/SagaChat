package com.dark.tool_neuron.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.Process
import com.dark.tool_neuron.R
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.engine.GenerationEvent
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.state.AppStateManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LLMService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val engine = GGUFEngine()

    private val binder = object : ILLMService.Stub() {

        override fun loadGgufModel(
            modelPath: String,
            modelName: String,
            loadingParams: String,
            inferenceParams: String,
            callback: IModelLoadCallback
        ) {
            // Launch in coroutine - non-blocking
            scope.launch(Dispatchers.IO) {
                try {
                    // Update state: Loading
                    AppStateManager.setLoadingModel(modelName)

                    val model = Model(
                        id = modelName,
                        modelPath = modelPath,
                        modelName = modelName,
                        pathType = PathType.FILE,
                        providerType = ProviderType.GGUF,
                        fileSize = null
                    )
                    val config = ModelConfig(
                        modelId = modelName,
                        modelLoadingParams = loadingParams,
                        modelInferenceParams = inferenceParams
                    )

                    val success = engine.load(model, config)

                    if (success) {
                        // Update state: Loaded
                        AppStateManager.setModelLoaded(modelName)
                        callback.onSuccess()
                    } else {
                        // Update state: Error
                        AppStateManager.setError("Failed to load model: $modelName")
                        callback.onError("Failed to load model")
                    }
                } catch (e: Exception) {
                    // Update state: Error
                    AppStateManager.setError(e.message ?: "Unknown error loading model")
                    callback.onError(e.message ?: "Unknown error")
                }
            }
        }

        override fun generateGguf(
            prompt: String,
            maxTokens: Int,
            callback: IGgufGenerationCallback
        ) {
            scope.launch(Dispatchers.IO) {
                try {
                    engine.generateFlow(prompt, maxTokens).collect { event ->
                        when (event) {
                            is GenerationEvent.Token -> {
                                callback.onToken(event.text)
                            }
                            is GenerationEvent.Done -> {
                                callback.onDone()
                            }
                            is GenerationEvent.Error -> {
                                callback.onError(event.message)
                            }
                            is GenerationEvent.Metrics -> {
                                callback.onMetrics(
                                    event.metrics.totalTokens,
                                    event.metrics.promptTokens,
                                    event.metrics.generatedTokens,
                                    event.metrics.tokensPerSecond,
                                    event.metrics.timeToFirstToken,
                                    event.metrics.totalTimeMs
                                )
                            }
                            is GenerationEvent.ToolCall -> {
                                callback.onToolCall(event.name, event.args)
                            }
                        }
                    }
                } catch (e: Exception) {
                    try {
                        callback.onError(e.message ?: "Unknown error")
                    } catch (_: Exception) {
                        // Client may have disconnected
                    }
                }
            }
        }

        override fun stopGenerationGguf() {
            engine.stopGeneration()
        }

        override fun unloadModelGguf() {
            scope.launch(Dispatchers.IO) {
                engine.unload()
                AppStateManager.setModelUnloaded()
            }
        }

        override fun getModelInfoGguf(): String? = engine.getModelInfo()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
        startForeground(1, createNotification())
    }

    override fun onDestroy() {
        scope.launch {
            engine.unload()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "llm_service"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId,
            "LLM Service",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)

        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("AI Model Service")
            .setContentText("Running...")
            .setSmallIcon(R.drawable.user)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }
}
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class LLMService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val engine = GGUFEngine()

    private val binder = object : ILLMService.Stub() {

        override fun loadGgufModel(
            modelPath: String, modelName: String, loadingParams: String, inferenceParams: String
        ): Boolean = runBlocking(Dispatchers.IO) {
            try {
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
                engine.load(model, config)
            } catch (_: Exception) {
                false
            }
        }

        override fun generateGguf(
            prompt: String, maxTokens: Int, callback: IGgufGenerationCallback
        ) {
            // Launch generation in a coroutine
            scope.launch(Dispatchers.Default) {
                try {
                    // Collect the flow - this handles all threading properly
                    engine.generateFlow(prompt, maxTokens).collect { event ->
                        // These callbacks go back to the client via Binder
                        // The Binder will handle thread switching on the client side
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
        // Use runBlocking here since we're in onDestroy and need to wait
        runBlocking {
            engine.unload()
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun createNotification(): Notification {
        val channelId = "llm_service"
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            channelId, "LLM Service", NotificationManager.IMPORTANCE_LOW
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
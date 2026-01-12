package com.dark.tool_neuron.engine

import android.content.Context
import android.util.Log
import com.dark.ai_sd.DiffusionBackendState
import com.dark.ai_sd.DiffusionGenerationParams
import com.dark.ai_sd.DiffusionGenerationState
import com.dark.ai_sd.DiffusionModelConfig
import com.dark.ai_sd.DiffusionRuntimeConfig
import com.dark.ai_sd.StableDiffusionManager
import com.dark.ai_sd.modelConfig
import kotlinx.coroutines.flow.StateFlow

object DiffusionEngine {

    private const val TAG = "DiffusionEngine"

    private lateinit var sdManager: StableDiffusionManager

    // Expose state flows to UI
    private lateinit var diffusionBackendState: StateFlow<DiffusionBackendState>
    private lateinit var diffusionGenerationState: StateFlow<DiffusionGenerationState>
    private lateinit var isGenerating: StateFlow<Boolean>


    fun init(context: Context) {
        try {
            sdManager = StableDiffusionManager.getInstance(context)
            diffusionBackendState = sdManager.diffusionBackendState
            diffusionGenerationState = sdManager.diffusionGenerationState
            isGenerating = sdManager.isGenerating
            sdManager.initialize(
                DiffusionRuntimeConfig(
                    "runtime_libs/qnnlibs", safetyCheckerEnabled = true
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Init failed: ${e.message}")
        }
    }

    fun loadModel(diffusionModelConfig: DiffusionModelConfig): Result<String> {
        val model = modelConfig {
            name("Stable Diffusion 1.5")
            modelDir("")
            textEmbeddingSize(768)
            runOnCpu(false)
            useCpuClip(true)
            setSafetyMode(true)
        }

        try {
            sdManager.loadModel(model, width = 512, height = 512).let {
                if (!it) {
                    return Result.failure(Exception("Failed to load model"))
                }
            }
            return Result.success("Model Loaded Successfully")
        } catch (e: Exception) {
            return Result.failure(e)
        }
    }

    fun generateImage(generationParams: DiffusionGenerationParams) {
        sdManager.generateImage(generationParams)
    }

    suspend fun observeBackendState() {
        diffusionBackendState.collect { state ->
            when (state) {
                is DiffusionBackendState.Idle -> {
                }

                is DiffusionBackendState.Starting -> {
                }

                is DiffusionBackendState.Running -> {
                }

                is DiffusionBackendState.Error -> {
                    state.message
                }
            }
        }
    }
}
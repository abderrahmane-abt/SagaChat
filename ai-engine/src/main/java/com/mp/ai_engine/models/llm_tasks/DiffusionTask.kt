package com.mp.ai_engine.models.llm_tasks

import android.graphics.Bitmap
import kotlinx.coroutines.CompletableDeferred

data class DiffusionTask(
    val id: String,
    val prompt: String,
    val negativePrompt: String = "",
    val steps: Int = 20,
    val cfg: Float = 7f,
    val seed: Long? = null,
    val width: Int = 512,
    val height: Int = 512,
    val denoiseStrength: Float = 0.6f,
    val useOpenCL: Boolean = true,
    val scheduler: String = "dpm",
    val inputImage: String? = null,
    val maskImage: String? = null,
    val events: DMStreamEvents,
    val result: CompletableDeferred<DiffusionResult>
)

data class DiffusionResult(
    val bitmap: Bitmap,
    val seed: Long?
)

interface DMStreamEvents {
    fun onProgress(progress: Float, step: Int, totalSteps: Int)
    fun onComplete(bitmap: Bitmap, seed: Long?)
    fun onError(error: String)
}
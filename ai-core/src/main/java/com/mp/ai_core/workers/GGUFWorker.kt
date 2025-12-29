package com.mp.ai_core.workers

import com.mp.ai_core.SuperModelWorker
import com.mp.ai_core.models.GGUFModelLoadingParameters
import com.mp.ai_core.models.GGUFModelTask
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.models.StreamCallback
import java.io.File

class GGUFWorker : SuperModelWorker<GGUFModelLoadingParameters, GGUFModelTask>() {

    private var nativeLib: GGUFNativeLib = GGUFNativeLib()

    override suspend fun loadModel(modelData: GGUFModelLoadingParameters): Result<String> {
        val modelFile = File(modelData.path)
        if (!modelFile.exists()) return Result.failure(Exception("Model file not found"))

        return try {
            val result = nativeLib.nativeLoadModel(
                path = modelData.path,
                threads = modelData.threads,
                ctxSize = modelData.ctxSize,
                temp = modelData.temp,
                topK = modelData.topK,
                topP = modelData.topP,
                minP = modelData.minP,
                mirostat = modelData.mirostat,
                mirostatTau = modelData.mirostatTau,
                mirostatEta = modelData.mirostatEta,
                seed = modelData.seed
            )

            if (result){
                Result.success("Model loaded successfully")
            }else{
                Result.failure(Exception("Failed to load model: Unknown Error"))
            }
        }catch (e: Exception){
            return Result.failure(e)
        }
    }

    override suspend fun runTask(task: GGUFModelTask) {
        val result = StringBuilder()
        nativeLib.nativeSetToolsJson(task.toolJson)
        nativeLib.nativeGenerateStream(
            prompt = task.input,
            maxTokens = task.maxTokens,
            callback = object : StreamCallback {
                override fun onToken(token: String) {
                    task.events.onToken(token)
                    result.append(token)
                }

                override fun onToolCall(name: String, argsJson: String) {
                    task.events.onTool(name, argsJson)
                }

                override fun onDone() {
                    task.result.complete(result.toString())
                }

                override fun onError(message: String) {
                    result.clear()
                    task.result.completeExceptionally(Exception(message))
                }
            }
        )
    }

    override fun unloadModel() {
        nativeLib.nativeRelease()
    }

    fun setSystemPrompt(systemPrompt: String){
        nativeLib.nativeSetSystemPrompt(systemPrompt)
    }

    fun setChatTemplate(chatTemplate: String){
        nativeLib.nativeSetChatTemplate(chatTemplate)
    }
}
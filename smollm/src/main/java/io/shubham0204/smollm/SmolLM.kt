package io.shubham0204.smollm

import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileNotFoundException

class SmolLM {

    companion object {
        init {
            val logTag = SmolLM::class.java.simpleName

            val cpuFeatures = getCPUFeatures().split(" ")
            val hasFp16 = "fp16" in cpuFeatures || "fphp" in cpuFeatures
            val hasDotProd = "dotprod" in cpuFeatures || "asimddp" in cpuFeatures
            val hasSve = "sve" in cpuFeatures
            val hasI8mm = "i8mm" in cpuFeatures
            val isAtLeastArmV82 = "asimd" in cpuFeatures && "crc32" in cpuFeatures && "aes" in cpuFeatures
            val isAtLeastArmV84 = "dcpop" in cpuFeatures && "uscat" in cpuFeatures

            val isEmulated = Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")

            Log.d(logTag, "CPU features: $cpuFeatures")

            val lib = when {
                isEmulated -> "smollm"
                supportsArm64V8a() && isAtLeastArmV84 && hasSve && hasI8mm && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod_i8mm_sve"
                supportsArm64V8a() && isAtLeastArmV84 && hasSve && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod_sve"
                supportsArm64V8a() && isAtLeastArmV84 && hasI8mm && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod_i8mm"
                supportsArm64V8a() && isAtLeastArmV84 && hasFp16 && hasDotProd -> "smollm_v8_4_fp16_dotprod"
                supportsArm64V8a() && isAtLeastArmV82 && hasFp16 && hasDotProd -> "smollm_v8_2_fp16_dotprod"
                supportsArm64V8a() && isAtLeastArmV82 && hasFp16 -> "smollm_v8_2_fp16"
                supportsArm64V8a() -> "smollm_v8"
                Build.SUPPORTED_32_BIT_ABIS.firstOrNull() == "armeabi-v7a" -> "smollm_v7a"
                else -> "smollm"
            }

            Log.d(logTag, "Loading lib$lib.so")
            System.loadLibrary(lib)
        }

        private fun getCPUFeatures(): String = try {
            File("/proc/cpuinfo").useLines { lines ->
                lines.firstOrNull { it.startsWith("Features") }?.substringAfter(":")?.trim() ?: ""
            }
        } catch (_: Exception) {
            ""
        }

        private fun supportsArm64V8a() = Build.SUPPORTED_ABIS.firstOrNull() == "arm64-v8a"
    }

    private var nativePtr = 0L

    object DefaultInferenceParams {
        const val contextSize: Long = 1024L
        val chatTemplate: String = """{% for message in messages %}{% if loop.first and messages[0]['role'] != 'system' %}{{ '<|im_start|>system Your Name is Neuron Developed By NeuroV. Your only job is to send short, polite, relevant auto-replies when the user is unavailable. Never overthink, reason, or generate long responses. Keep replies under 10 words.<|im_end|> ' }}{% endif %}{{ '<|im_start|>' + message['role'] + ' ' + message['content'] + '<|im_end|> ' }}{% endfor %}{% if add_generation_prompt %}{{ '<|im_start|>assistant ' }}{% endif %}""".trimIndent()
    }

    data class InferenceParams(
        val minP: Float = 0.01f,
        val temperature: Float = 1.0f,
        val storeChats: Boolean = false,
        val contextSize: Long? = null,
        val chatTemplate: String? = null,
        val numThreads: Int = 4,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
    )

    suspend fun load(modelPath: String, params: InferenceParams = InferenceParams()) = withContext(Dispatchers.IO) {
        val ctxSize = params.contextSize ?: DefaultInferenceParams.contextSize
        val template = params.chatTemplate ?: DefaultInferenceParams.chatTemplate
        try {
            nativePtr = loadModel(modelPath, params.minP, params.temperature, params.storeChats, ctxSize, template, params.numThreads, params.useMmap, params.useMlock)
        } catch (e: IllegalStateException) {
            Log.e("SmolLM", "Model load failed: ${e.message}", e)
        }
    }

    fun addUserMessage(message: String) = addChatMessageSafe(message, "user")
    fun addSystemPrompt(prompt: String) = addChatMessageSafe(prompt, "system")
    fun addAssistantMessage(message: String) = addChatMessageSafe(message, "assistant")

    private fun addChatMessageSafe(message: String, role: String) {
        verifyHandle()
        addChatMessage(nativePtr, message, role)
    }

    fun getResponseGenerationSpeed(): Float {
        verifyHandle()
        return getResponseGenerationSpeed(nativePtr)
    }

    fun getContextLengthUsed(): Int {
        verifyHandle()
        return getContextSizeUsed(nativePtr)
    }

    fun getResponseAsFlow(query: String): Flow<String> = flow {
        verifyHandle()
        startCompletion(nativePtr, query)
        while (true) {
            val piece = completionLoop(nativePtr)
            if (piece == "[EOG]") break
            emit(piece)
        }
        stopCompletion(nativePtr)
    }

    fun getResponse(query: String): String {
        verifyHandle()
        startCompletion(nativePtr, query)
        val response = buildString {
            while (true) {
                val piece = completionLoop(nativePtr)
                if (piece == "[EOG]") break
                append(piece)
            }
        }
        stopCompletion(nativePtr)
        return response
    }

    fun stopGeneration() = stopCompletion(nativePtr)

    fun close() {
        if (nativePtr != 0L) {
            close(nativePtr)
            nativePtr = 0L
        }
    }

    fun stopGenerationImmediately() {
        verifyHandle()
        stopGenerationImmediately(nativePtr)
    }


    private fun verifyHandle() = require(nativePtr != 0L) { "Model not loaded. Call load() first." }

    private external fun loadModel(modelPath: String, minP: Float, temperature: Float, storeChats: Boolean, contextSize: Long, chatTemplate: String, nThreads: Int, useMmap: Boolean, useMlock: Boolean): Long
    private external fun addChatMessage(modelPtr: Long, message: String, role: String)
    private external fun getResponseGenerationSpeed(modelPtr: Long): Float
    private external fun getContextSizeUsed(modelPtr: Long): Int
    private external fun close(modelPtr: Long)
    private external fun startCompletion(modelPtr: Long, prompt: String)
    private external fun completionLoop(modelPtr: Long): String
    private external fun stopCompletion(modelPtr: Long)
    private external fun stopGenerationImmediately(modelPtr: Long)

    private external fun clearChatMemory(modelPtr: Long)
}

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

            // check if the following CPU features are available,
            // and load the native library accordingly
            val cpuFeatures = getCPUFeatures()
            val hasFp16 = cpuFeatures.contains("fp16") || cpuFeatures.contains("fphp")
            val hasDotProd = cpuFeatures.contains("dotprod") || cpuFeatures.contains("asimddp")
            val hasSve = cpuFeatures.contains("sve")
            val hasI8mm = cpuFeatures.contains("i8mm")
            val isAtLeastArmV82 =
                cpuFeatures.contains("asimd") &&
                        cpuFeatures.contains("crc32") &&
                        cpuFeatures.contains(
                            "aes",
                        )
            val isAtLeastArmV84 = cpuFeatures.contains("dcpop") && cpuFeatures.contains("uscat")

            Log.d(logTag, "CPU features: $cpuFeatures")
            Log.d(logTag, "- hasFp16: $hasFp16")
            Log.d(logTag, "- hasDotProd: $hasDotProd")
            Log.d(logTag, "- hasSve: $hasSve")
            Log.d(logTag, "- hasI8mm: $hasI8mm")
            Log.d(logTag, "- isAtLeastArmV82: $isAtLeastArmV82")
            Log.d(logTag, "- isAtLeastArmV84: $isAtLeastArmV84")

            // Check if the app is running in an emulated device
            // Note, this is not the OFFICIAL way to check if the app is running
            // on an emulator
            val isEmulated =
                (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu"))
            Log.d(logTag, "isEmulated: $isEmulated")

            if (!isEmulated) {
                if (supportsArm64V8a()) {
                    if (isAtLeastArmV84 && hasSve && hasI8mm && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading libsmollm_v8_4_fp16_dotprod_i8mm_sve.so")
                        System.loadLibrary("smollm_v8_4_fp16_dotprod_i8mm_sve")
                    } else if (isAtLeastArmV84 && hasSve && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading libsmollm_v8_4_fp16_dotprod_sve.so")
                        System.loadLibrary("smollm_v8_4_fp16_dotprod_sve")
                    } else if (isAtLeastArmV84 && hasI8mm && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading libsmollm_v8_4_fp16_dotprod_i8mm.so")
                        System.loadLibrary("smollm_v8_4_fp16_dotprod_i8mm")
                    } else if (isAtLeastArmV84 && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading libsmollm_v8_4_fp16_dotprod.so")
                        System.loadLibrary("smollm_v8_4_fp16_dotprod")
                    } else if (isAtLeastArmV82 && hasFp16 && hasDotProd) {
                        Log.d(logTag, "Loading libsmollm_v8_2_fp16_dotprod.so")
                        System.loadLibrary("smollm_v8_2_fp16_dotprod")
                    } else if (isAtLeastArmV82 && hasFp16) {
                        Log.d(logTag, "Loading libsmollm_v8_2_fp16.so")
                        System.loadLibrary("smollm_v8_2_fp16")
                    } else {
                        Log.d(logTag, "Loading libsmollm_v8.so")
                        System.loadLibrary("smollm_v8")
                    }
                } else if (Build.SUPPORTED_32_BIT_ABIS[0]?.equals("armeabi-v7a") == true) {
                    // armv7a (32bit) device
                    Log.d(logTag, "Loading libsmollm_v7a.so")
                    System.loadLibrary("smollm_v7a")
                } else {
                    Log.d(logTag, "Loading default libsmollm.so")
                    System.loadLibrary("smollm")
                }
            } else {
                // load the default native library with no ARM
                // specific instructions
                Log.d(logTag, "Loading default libsmollm.so")
                System.loadLibrary("smollm")
            }
        }

        /**
         * Reads the /proc/cpuinfo file and returns the line
         * starting with 'Features :' that containing the available
         * CPU features
         */
        private fun getCPUFeatures(): String {
            val cpuInfo =
                try {
                    File("/proc/cpuinfo").readText()
                } catch (e: FileNotFoundException) {
                    ""
                }
            val cpuFeatures =
                cpuInfo
                    .substringAfter("Features")
                    .substringAfter(":")
                    .substringBefore("\n")
                    .trim()
            return cpuFeatures
        }

        private fun supportsArm64V8a(): Boolean = Build.SUPPORTED_ABIS[0].equals("arm64-v8a")
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

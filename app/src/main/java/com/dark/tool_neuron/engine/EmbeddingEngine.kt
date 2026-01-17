package com.dark.tool_neuron.engine

import android.content.Context
import android.util.Log
import com.mp.ai_gguf.GGUFNativeLib
import com.mp.ai_gguf.models.EmbeddingCallback
import com.mp.ai_gguf.models.EmbeddingResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class EmbeddingConfig(
    val modelPath: String,
    val threads: Int = 0,
    val contextSize: Int = 512,
    val normalize: Boolean = true
)

class EmbeddingEngine {
    private val nativeLib = GGUFNativeLib()
    private var config: EmbeddingConfig? = null
    private var dimension: Int = 0

    suspend fun initialize(config: EmbeddingConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(config.modelPath)
            if (!modelFile.exists()) {
                return@withContext Result.failure(Exception("Model file not found: ${config.modelPath}"))
            }

            val success = nativeLib.nativeLoadEmbeddingModel(
                path = config.modelPath,
                threads = config.threads,
                contextSize = config.contextSize
            )

            if (!success) {
                return@withContext Result.failure(Exception("Failed to load embedding model"))
            }

            val modelInfo = nativeLib.nativeGetEmbeddingModelInfo()

            val testResult = embed("test")
            if (testResult == null) {
                return@withContext Result.failure(Exception("Failed to generate test embedding"))
            }
            dimension = testResult.size

            this@EmbeddingEngine.config = config
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { continuation ->
            val resumed = AtomicBoolean(false)

            val callback = object : EmbeddingCallback {
                override fun onComplete(result: EmbeddingResult) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resume(result.embeddings)
                    } else {
                        Log.w("EmbeddingEngine", "Callback fired after continuation already resumed")
                    }
                }

                override fun onError(message: String) {
                    if (resumed.compareAndSet(false, true)) {
                        continuation.resumeWithException(Exception(message))
                    } else {
                        Log.w("EmbeddingEngine", "Error callback fired after continuation already resumed: $message")
                    }
                }
            }

            val success = nativeLib.nativeEncodeText(
                text = text,
                normalize = config?.normalize ?: true,
                callback = callback
            )

            if (!success) {
                if (resumed.compareAndSet(false, true)) {
                    continuation.resumeWithException(Exception("Failed to start encoding - native call returned false"))
                }
            }
        }
    }

    suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        texts.map { embed(it) }
    }

    fun isInitialized(): Boolean = config != null && dimension > 0

    fun getDimension(): Int = dimension

    fun getModelName(): String = config?.modelPath?.substringAfterLast("/") ?: "unknown"

    fun close() {
        nativeLib.nativeReleaseEmbeddingModel()
        config = null
        dimension = 0
    }

    companion object {
        fun getModelPath(context: Context): File {
            return File(context.filesDir, "embedding_model/all-MiniLM-L6-v2-Q5_K_M.gguf")
        }

        fun isModelDownloaded(context: Context): Boolean {
            return getModelPath(context).exists()
        }
    }
}

package com.dark.tool_neuron.neuron_example

import com.ml.shubham0204.sentence_embeddings.SentenceEmbedding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Abstract interface for embedding generation.
 * Allows swapping out the underlying embedding library.
 */
interface EmbeddingProvider {
    suspend fun initialize(config: EmbeddingConfig): Result<Unit>
    suspend fun embed(text: String): FloatArray?
    suspend fun embedBatch(texts: List<String>): List<FloatArray?>
    fun isInitialized(): Boolean
    fun getDimension(): Int
    fun getModelName(): String
    fun close()
}

data class EmbeddingConfig(
    val modelPath: String,
    val tokenizerPath: String,
    val modelName: String = "default",
    val useFP16: Boolean = true,
    val useXNNPack: Boolean = false,
    val normalizeEmbeddings: Boolean = true
)

/**
 * Default implementation using SentenceEmbedding library.
 * Can be replaced with any other embedding library.
 */
class SentenceEmbeddingProvider : EmbeddingProvider {
    private var sentenceEmbedding: SentenceEmbedding? = null
    private var config: EmbeddingConfig? = null
    private var dimension: Int = 0

    override suspend fun initialize(config: EmbeddingConfig): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(config.modelPath)
            val tokenizerFile = File(config.tokenizerPath)

            if (!modelFile.exists()) {
                return@withContext Result.failure(Exception("Model file not found: ${config.modelPath}"))
            }
            if (!tokenizerFile.exists()) {
                return@withContext Result.failure(Exception("Tokenizer file not found: ${config.tokenizerPath}"))
            }

            val tokenizerBytes = tokenizerFile.readBytes()
            sentenceEmbedding = SentenceEmbedding().apply {
                init(
                    modelFilepath = config.modelPath,
                    tokenizerBytes = tokenizerBytes,
                    useTokenTypeIds = true,
                    outputTensorName = "sentence_embedding",
                    useFP16 = config.useFP16,
                    useXNNPack = config.useXNNPack,
                    normalizeEmbeddings = config.normalizeEmbeddings
                )
            }

            // Get dimension from a test embedding
            val testEmbed = sentenceEmbedding?.encode("test")
            dimension = testEmbed?.size ?: 0

            this@SentenceEmbeddingProvider.config = config
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.IO) {
        sentenceEmbedding?.encode(text)
    }

    override suspend fun embedBatch(texts: List<String>): List<FloatArray?> = withContext(Dispatchers.IO) {
        texts.map { sentenceEmbedding?.encode(it) }
    }

    override fun isInitialized(): Boolean = sentenceEmbedding != null

    override fun getDimension(): Int = dimension

    override fun getModelName(): String = config?.modelName ?: "unknown"

    override fun close() {
        sentenceEmbedding = null
        config = null
        dimension = 0
    }
}
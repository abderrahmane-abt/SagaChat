package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import com.dark.gguf_lib.RAGEngine
import com.dark.tool_neuron.model.ChatDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RagManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepo: DocumentRepository,
    private val documentParser: DocumentParser,
) {
    private val chatEngines = mutableMapOf<String, RAGEngine>()
    private val engineLocks = mutableMapOf<String, Mutex>()
    private val mutex = Mutex()

    private var embeddingModelPath: String? = null

    fun isEmbeddingModelLoaded(): Boolean = embeddingModelPath != null

    suspend fun loadEmbeddingModel(path: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            embeddingModelPath = path
            true
        }
    }

    suspend fun addDocumentToChat(
        chatId: String,
        uri: Uri,
        fileName: String,
        fileSize: Long
    ): Result<ChatDocument> = withContext(Dispatchers.IO) {
        val modelPath = embeddingModelPath
            ?: return@withContext Result.failure(IllegalStateException("Embedding model not loaded"))

        val parsed = documentParser.extractText(uri)
        if (parsed.isFailure) return@withContext Result.failure(parsed.exceptionOrNull()!!)

        val (text, mimeType) = parsed.getOrThrow()
        val docId = UUID.randomUUID().toString()

        val (engine, lock) = mutex.withLock {
            val e = getOrCreateEngine(chatId, modelPath)
                ?: return@withContext Result.failure(IllegalStateException("Failed to initialize RAG engine"))
            val l = engineLocks.getOrPut(chatId) { Mutex() }
            e to l
        }

        val chunkCount = lock.withLock { engine.addDocument(text, docId) }
        if (chunkCount <= 0) {
            return@withContext Result.failure(IllegalStateException("Document produced no chunks"))
        }

        val doc = ChatDocument(
            id = docId,
            chatId = chatId,
            name = fileName,
            mimeType = mimeType,
            chunkCount = chunkCount,
            sizeBytes = fileSize,
        )
        documentRepo.addDocument(doc)
        Result.success(doc)
    }

    suspend fun queryChat(chatId: String, query: String): String? = withContext(Dispatchers.IO) {
        val (engine, lock) = mutex.withLock {
            val e = chatEngines[chatId] ?: return@withContext null
            val l = engineLocks.getOrPut(chatId) { Mutex() }
            e to l
        }
        val results = lock.withLock { engine.query(query) }
        if (results.isNullOrEmpty()) return@withContext null
        results.joinToString("\n\n") { it.text }
    }

    suspend fun buildAugmentedPrompt(
        chatId: String,
        query: String,
        originalPrompt: String
    ): String = withContext(Dispatchers.IO) {
        val (engine, lock) = mutex.withLock {
            val e = chatEngines[chatId] ?: return@withContext originalPrompt
            val l = engineLocks.getOrPut(chatId) { Mutex() }
            e to l
        }
        val augmented = lock.withLock { engine.buildPrompt(query, originalPrompt) }
        augmented ?: originalPrompt
    }

    fun releaseChat(chatId: String) {
        val engine = chatEngines.remove(chatId) ?: return
        engineLocks.remove(chatId)
        engine.close()
        documentRepo.removeAllForChat(chatId)
    }

    fun releaseAll() {
        chatEngines.values.forEach { it.close() }
        chatEngines.clear()
        engineLocks.clear()
    }

    private fun getOrCreateEngine(chatId: String, modelPath: String): RAGEngine? {
        chatEngines[chatId]?.let { return it }
        val engine = RAGEngine()
        val created = engine.create(
            THREADS, CHUNK_SIZE, CHUNK_OVERLAP, DIMS, TOP_K, TOP_N, LATE_CHUNKING
        )
        if (!created) return null
        if (!engine.loadModel(modelPath)) {
            engine.close()
            return null
        }
        chatEngines[chatId] = engine
        return engine
    }

    companion object {
        private const val THREADS = 4
        private const val CHUNK_SIZE = 256
        private const val CHUNK_OVERLAP = 32
        private const val DIMS = 256
        private const val TOP_K = 32
        private const val TOP_N = 5
        private const val LATE_CHUNKING = false
    }
}

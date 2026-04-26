package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dark.gguf_lib.RAGEngine
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.enums.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RagManager"

@Singleton
class RagManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val documentRepo: DocumentRepository,
    private val modelRepo: ModelRepository,
    private val ragPrefs: RagPreferences,
) {
    private val engine = RAGEngine()
    private val opsMutex = Mutex()
    private val readyMutex = Mutex()
    private val ingestedDocIds = mutableSetOf<String>()
    private val sourcesDir: File by lazy {
        File(context.filesDir, "chat_documents/sources").apply { mkdirs() }
    }

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _activeEmbeddingName = MutableStateFlow<String?>(null)
    val activeEmbeddingName: StateFlow<String?> = _activeEmbeddingName.asStateFlow()

    val defaultEmbeddingModelId: StateFlow<String?> = ragPrefs.defaultEmbeddingModelId

    fun hasEmbeddingModelInstalled(): Boolean =
        modelRepo.models.value.any { it.providerType == ProviderType.EMBEDDING }

    fun documentsForChat(chatId: String): List<ChatDocument> =
        documentRepo.getDocumentsForChat(chatId)

    fun allDocuments(): List<ChatDocument> = documentRepo.getAllDocuments()

    fun setDefaultEmbeddingModelId(modelId: String?) {
        ragPrefs.setDefaultEmbeddingModelId(modelId)
    }

    suspend fun ensureReady(): Boolean = readyMutex.withLock {
        if (_isReady.value) return@withLock true

        if (!engine.isCreated) {
            val created = withContext(Dispatchers.IO) {
                engine.create(
                    threads = 0,
                    chunkSize = 256,
                    chunkOverlap = 32,
                    dims = 256,
                    topK = 32,
                    topN = 5,
                    lateChunking = true,
                )
            }
            if (!created) {
                Log.e(TAG, "Failed to create RAG engine")
                return@withLock false
            }
        }

        val model = pickEmbeddingModel() ?: run {
            Log.w(TAG, "No embedding model installed")
            return@withLock false
        }

        val loaded = withContext(Dispatchers.IO) { engine.loadModel(model.path) }
        if (!loaded) {
            Log.e(TAG, "Failed to load embedding model at ${model.path}")
            return@withLock false
        }

        _activeEmbeddingName.value = model.name
        _isReady.value = true
        Log.i(TAG, "RAG ready with model: ${model.name}")
        true
    }

    private fun pickEmbeddingModel(): com.dark.tool_neuron.model.ModelInfo? {
        val installed = modelRepo.models.value.filter { it.providerType == ProviderType.EMBEDDING }
        if (installed.isEmpty()) return null
        val preferredId = ragPrefs.defaultEmbeddingModelId.value
        return installed.firstOrNull { it.id == preferredId } ?: installed.first()
    }

    suspend fun hydrateChat(chatId: String): List<ChatDocument> = withContext(Dispatchers.IO) {
        val records = documentRepo.getDocumentsForChat(chatId)
        if (records.isEmpty()) return@withContext emptyList()
        if (!ensureReady()) return@withContext records

        records.forEach { doc ->
            if (doc.id in ingestedDocIds) return@forEach
            if (doc.sourceId.isBlank()) return@forEach
            val srcFile = sourceFile(doc.sourceId)
            if (!srcFile.exists()) return@forEach
            val bytes = runCatching { srcFile.readBytes() }.getOrNull() ?: return@forEach
            val chunks = opsMutex.withLock {
                engine.ingestBytes(bytes, doc.mimeType, doc.name, doc.id)
            }
            if (chunks >= 0) ingestedDocIds += doc.id
            else Log.w(TAG, "hydrate failed for ${doc.id}: code=$chunks")
        }
        records
    }

    suspend fun ingestDocument(
        chatId: String,
        uri: Uri,
        displayName: String,
        size: Long,
        mimeType: String?,
    ): Result<ChatDocument> = withContext(Dispatchers.IO) {
        if (!ensureReady()) {
            return@withContext Result.failure(
                IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store.")
            )
        }

        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Could not read document"))

        if (bytes.isEmpty()) return@withContext Result.failure(IllegalStateException("Document is empty"))

        val effectiveMime = mimeType ?: context.contentResolver.getType(uri)
        val sourceId = sha256Hex(bytes)
        val docId = "$chatId:$sourceId"

        val existing = documentRepo.getDocumentsForChat(chatId).firstOrNull { it.id == docId }
        if (existing != null) return@withContext Result.success(existing)

        persistSource(sourceId, bytes)

        val chunkCount = opsMutex.withLock {
            engine.ingestBytes(bytes, effectiveMime, displayName, docId)
        }

        when {
            chunkCount == -1 -> return@withContext Result.failure(
                IllegalStateException("Unsupported document format")
            )
            chunkCount == -2 -> return@withContext Result.failure(
                IllegalStateException("Could not parse document")
            )
            chunkCount == -3 -> return@withContext Result.failure(
                IllegalStateException("Document contains no readable text")
            )
            chunkCount < 0 -> return@withContext Result.failure(
                IllegalStateException("Indexing failed (code $chunkCount)")
            )
        }

        ingestedDocIds += docId
        val doc = ChatDocument(
            id = docId,
            chatId = chatId,
            sourceId = sourceId,
            name = displayName,
            mimeType = effectiveMime ?: "application/octet-stream",
            chunkCount = chunkCount,
            sizeBytes = size,
        )
        documentRepo.addDocument(doc)
        Result.success(doc)
    }

    suspend fun attachExisting(
        currentChatId: String,
        source: ChatDocument,
    ): Result<ChatDocument> = withContext(Dispatchers.IO) {
        if (source.sourceId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Source document is unavailable"))
        }
        if (!ensureReady()) {
            return@withContext Result.failure(
                IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store.")
            )
        }
        val srcFile = sourceFile(source.sourceId)
        if (!srcFile.exists()) {
            return@withContext Result.failure(IllegalStateException("Source bytes for ${source.name} are missing"))
        }
        val docId = "$currentChatId:${source.sourceId}"
        documentRepo.getDocumentsForChat(currentChatId).firstOrNull { it.id == docId }?.let {
            return@withContext Result.success(it)
        }
        val bytes = runCatching { srcFile.readBytes() }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Could not read stored bytes"))

        val chunkCount = opsMutex.withLock {
            engine.ingestBytes(bytes, source.mimeType, source.name, docId)
        }
        if (chunkCount < 0) {
            return@withContext Result.failure(IllegalStateException("Indexing failed (code $chunkCount)"))
        }
        ingestedDocIds += docId
        val doc = ChatDocument(
            id = docId,
            chatId = currentChatId,
            sourceId = source.sourceId,
            name = source.name,
            mimeType = source.mimeType,
            chunkCount = chunkCount,
            sizeBytes = source.sizeBytes,
        )
        documentRepo.addDocument(doc)
        Result.success(doc)
    }

    suspend fun removeDocument(docId: String) = withContext(Dispatchers.IO) {
        opsMutex.withLock { engine.removeDocument(docId) }
        ingestedDocIds -= docId
        val doc = documentRepo.getAllDocuments().firstOrNull { it.id == docId }
        documentRepo.removeDocument(docId)
        val sourceId = doc?.sourceId
        if (!sourceId.isNullOrBlank() && documentRepo.countWithSource(sourceId) == 0) {
            sourceFile(sourceId).delete()
        }
    }

    suspend fun buildAugmentedPrompt(
        chatId: String,
        query: String,
        originalPrompt: String,
    ): String = withContext(Dispatchers.IO) {
        if (!_isReady.value) return@withContext originalPrompt
        val results = opsMutex.withLock { engine.query(query) }
        val scoped = results.filter { it.docId.startsWith("$chatId:") }
        if (scoped.isEmpty()) return@withContext originalPrompt

        buildString {
            append("Use the following context from the user's documents to answer. ")
            append("If it does not help, fall back to your general knowledge.\n\n")
            append("<context>\n")
            scoped.forEachIndexed { index, result ->
                append("[${index + 1}] ")
                append(result.text.trim())
                append("\n\n")
            }
            append("</context>\n\n")
            append(originalPrompt)
        }
    }

    suspend fun release() = opsMutex.withLock {
        engine.close()
        ingestedDocIds.clear()
        _isReady.value = false
        _activeEmbeddingName.value = null
    }

    private fun sourceFile(sourceId: String): File = File(sourcesDir, "$sourceId.bin")

    private fun persistSource(sourceId: String, bytes: ByteArray) {
        val target = sourceFile(sourceId)
        if (target.exists()) return
        val tmp = File(sourcesDir, "$sourceId.bin.tmp")
        tmp.outputStream().use { it.write(bytes) }
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
    }
}

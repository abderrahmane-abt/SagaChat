package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import android.util.Log
import com.dark.gguf_lib.RAGEngine
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.ChatDocument
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val keywordIndex: RagKeywordIndex,
    private val reranker: RagReranker,
    private val queryRewriter: RagQueryRewriter,
    private val docSummarizer: RagDocSummarizer,
    private val raptor: RagRaptor,
    private val appPrefs: AppPreferences,
    private val sourceVault: SourceFileVault,
    private val cache: RagCacheRepository,
) {
    private val engine = RAGEngine()
    private val opsMutex = Mutex()
    private val readyMutex = Mutex()
    private val raptorMutex = Mutex()
    private val ingestedDocIds = mutableSetOf<String>()

    @Volatile private var activeFingerprint: String = ""
    @Volatile private var loadedSources: Set<String> = emptySet()

    private val _isReady = MutableStateFlow(false)
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    private val _activeEmbeddingName = MutableStateFlow<String?>(null)
    val activeEmbeddingName: StateFlow<String?> = _activeEmbeddingName.asStateFlow()

    private val _retrievalStatus = MutableStateFlow<RetrievalStatus>(RetrievalStatus.Idle)
    val retrievalStatus: StateFlow<RetrievalStatus> = _retrievalStatus.asStateFlow()

    sealed interface RetrievalStatus {
        data object Idle : RetrievalStatus
        data object RewritingQuery : RetrievalStatus
        data object Searching : RetrievalStatus
        data object Reranking : RetrievalStatus
    }

    private val _deepIndexing = MutableStateFlow<Set<String>>(emptySet())
    val deepIndexing: StateFlow<Set<String>> = _deepIndexing.asStateFlow()

    private val _raptorBuilding = MutableStateFlow<Set<String>>(emptySet())
    val raptorBuilding: StateFlow<Set<String>> = _raptorBuilding.asStateFlow()

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
                    topK = 64,
                    topN = DENSE_CANDIDATES,
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

        activeFingerprint = model.id.ifBlank { model.path }
        runCatching { cache.invalidateForeign(activeFingerprint) }
            .onFailure { Log.w(TAG, "cache invalidate failed", it) }

        _activeEmbeddingName.value = model.name
        _isReady.value = true
        Log.i(TAG, "RAG ready with model: ${model.name} (fp=$activeFingerprint)")
        true
    }

    private fun pickEmbeddingModel(): ModelInfo? {
        val installed = modelRepo.models.value.filter { it.providerType == ProviderType.EMBEDDING }
        if (installed.isEmpty()) return null
        val preferredId = ragPrefs.defaultEmbeddingModelId.value
        return installed.firstOrNull { it.id == preferredId } ?: installed.first()
    }

    suspend fun hydrateChat(chatId: String): List<ChatDocument> = withContext(Dispatchers.IO) {
        val records = documentRepo.getDocumentsForChat(chatId)
        if (records.isEmpty()) {
            opsMutex.withLock { swapEngineSources(emptySet()) }
            return@withContext emptyList()
        }
        if (!ensureReady()) return@withContext records

        val target = records.mapNotNull { it.sourceId.takeIf { id -> id.isNotBlank() } }.toSet()

        opsMutex.withLock { swapEngineSources(target, records) }
        records
    }

    private suspend fun swapEngineSources(
        target: Set<String>,
        records: List<ChatDocument> = emptyList(),
    ) {
        val current = loadedSources
        val toRemove = current - target
        val toAdd = target - current

        toRemove.forEach { sourceId ->
            removeSourceFromEngine(sourceId)
        }
        toAdd.forEach { sourceId ->
            val record = records.firstOrNull { it.sourceId == sourceId }
            ensureSourceInEngine(sourceId, record)
        }

        loadedSources = target
    }

    private fun removeSourceFromEngine(sourceId: String) {
        if (sourceId.isBlank()) return
        val toDrop = ingestedDocIds.filter { it == sourceId || it.startsWith("$sourceId::") }
        toDrop.forEach { engine.removeDocument(it) }
        ingestedDocIds -= toDrop.toSet()
    }

    private suspend fun ensureSourceInEngine(sourceId: String, record: ChatDocument?) {
        if (sourceId.isBlank()) return
        if (sourceId in ingestedDocIds) return

        val cached = runCatching { cache.readBySource(sourceId) }.getOrNull().orEmpty()
        if (cached.isNotEmpty()) {
            var anyImported = false
            cached.forEach { entry ->
                if (entry.fingerprint != activeFingerprint) return@forEach
                val rc = engine.importDoc(entry.blob)
                if (rc == 0) {
                    ingestedDocIds += entry.docId
                    anyImported = true
                } else {
                    Log.w(TAG, "cache importDoc rc=$rc for ${entry.docId}; will fall back to re-embed")
                }
            }
            if (anyImported) return
            cache.removeBySource(sourceId)
        }

        if (record == null) {
            Log.w(TAG, "ensureSourceInEngine($sourceId): no metadata record, cannot re-embed")
            return
        }
        reembedSource(sourceId, record)
    }

    private suspend fun reembedSource(sourceId: String, record: ChatDocument) {
        val bytes = sourceVault.read(sourceId) ?: run {
            Log.w(TAG, "source $sourceId missing on disk")
            return
        }
        val n = engine.ingestBytes(bytes, record.mimeType, record.name, sourceId)
        if (n < 0) {
            Log.w(TAG, "re-embed failed for $sourceId: rc=$n")
            return
        }
        ingestedDocIds += sourceId
        cacheCurrentDoc(sourceId, sourceId)
        indexKeywordsIfTextLike(sourceId, record.name, record.mimeType, bytes)
    }

    private fun cacheCurrentDoc(docId: String, sourceId: String) {
        val blob = engine.exportDoc(docId) ?: return
        runCatching {
            cache.write(
                RagCacheEntry(
                    docId = docId,
                    sourceId = sourceId,
                    fingerprint = activeFingerprint,
                    blob = blob,
                ),
            )
        }.onFailure { Log.w(TAG, "cache write failed for $docId", it) }
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
                IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store."),
            )
        }

        val bytes = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        }.getOrNull()
            ?: return@withContext Result.failure(IllegalStateException("Could not read document"))
        if (bytes.isEmpty()) return@withContext Result.failure(IllegalStateException("Document is empty"))

        val effectiveMime = mimeType ?: context.contentResolver.getType(uri)
        val sourceId = sha256Hex(bytes)
        val docMetaId = "$chatId:$sourceId"

        val existing = documentRepo.getDocumentsForChat(chatId).firstOrNull { it.id == docMetaId }
        if (existing != null) return@withContext Result.success(existing)

        if (!sourceVault.write(sourceId, bytes)) {
            return@withContext Result.failure(IllegalStateException("Could not store document bytes"))
        }

        val resolvedMime = effectiveMime ?: "application/octet-stream"

        val chunkCount = opsMutex.withLock {
            if (sourceId in ingestedDocIds) {
                engine.exportDoc(sourceId)?.let { /* already loaded; fall through to meta write */ }
                keywordIndex.docCount(sourceId)
            } else {
                val cached = runCatching { cache.readBySource(sourceId) }.getOrNull().orEmpty()
                val importedCount = if (cached.isNotEmpty()) {
                    var ok = 0
                    cached.forEach { entry ->
                        if (entry.fingerprint != activeFingerprint) return@forEach
                        if (engine.importDoc(entry.blob) == 0) {
                            ingestedDocIds += entry.docId
                            ok++
                        }
                    }
                    if (ok > 0) keywordIndex.docCount(sourceId).coerceAtLeast(1) else -1
                } else -1

                if (importedCount < 0) {
                    val n = engine.ingestBytes(bytes, resolvedMime, displayName, sourceId)
                    if (n < 0) {
                        return@withLock n
                    }
                    ingestedDocIds += sourceId
                    cacheCurrentDoc(sourceId, sourceId)
                    indexKeywordsIfTextLike(sourceId, displayName, resolvedMime, bytes)
                    n
                } else {
                    importedCount
                }
            }
        }

        when {
            chunkCount == -1 -> return@withContext Result.failure(IllegalStateException("Unsupported document format"))
            chunkCount == -2 -> return@withContext Result.failure(IllegalStateException("Could not parse document"))
            chunkCount == -3 -> return@withContext Result.failure(IllegalStateException("Document contains no readable text"))
            chunkCount < 0 -> return@withContext Result.failure(IllegalStateException("Indexing failed (code $chunkCount)"))
        }

        loadedSources = loadedSources + sourceId
        val doc = ChatDocument(
            id = docMetaId,
            chatId = chatId,
            sourceId = sourceId,
            name = displayName,
            mimeType = resolvedMime,
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
        if (!sourceVault.exists(source.sourceId)) {
            return@withContext Result.failure(IllegalStateException("Source bytes for ${source.name} are missing"))
        }
        val docMetaId = "$currentChatId:${source.sourceId}"
        documentRepo.getDocumentsForChat(currentChatId).firstOrNull { it.id == docMetaId }?.let {
            return@withContext Result.success(it)
        }

        if (ensureReady()) {
            opsMutex.withLock {
                if (source.sourceId !in ingestedDocIds) {
                    ensureSourceInEngine(source.sourceId, source)
                }
            }
            loadedSources = loadedSources + source.sourceId
        }

        val doc = ChatDocument(
            id = docMetaId,
            chatId = currentChatId,
            sourceId = source.sourceId,
            name = source.name,
            mimeType = source.mimeType,
            chunkCount = source.chunkCount,
            sizeBytes = source.sizeBytes,
            isDeepIndexed = source.isDeepIndexed,
            isRaptorIndexed = source.isRaptorIndexed,
        )
        documentRepo.addDocument(doc)
        Result.success(doc)
    }

    suspend fun deepIndex(docMetaId: String): Result<ChatDocument> = withContext(Dispatchers.IO) {
        val doc = documentRepo.getDocument(docMetaId)
            ?: return@withContext Result.failure(IllegalStateException("Document not found"))
        if (doc.isDeepIndexed) return@withContext Result.success(doc)
        if (!isTextLike(doc.mimeType, doc.name)) {
            return@withContext Result.failure(IllegalStateException("Deep Index only supports text-format documents (txt, md, json, code, etc.)"))
        }
        if (doc.sourceId.isBlank()) {
            return@withContext Result.failure(IllegalStateException("Source bytes missing"))
        }
        if (!sourceVault.exists(doc.sourceId)) {
            return@withContext Result.failure(IllegalStateException("Source file missing on disk"))
        }
        if (!ensureReady()) {
            return@withContext Result.failure(IllegalStateException("RAG engine not ready"))
        }

        val bytes = sourceVault.read(doc.sourceId)
            ?: return@withContext Result.failure(IllegalStateException("Failed to read source"))
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: return@withContext Result.failure(IllegalStateException("Source contains no text"))

        _deepIndexing.value = _deepIndexing.value + docMetaId
        try {
            val summary = docSummarizer.summarize(doc.name, doc.mimeType, text)
                ?: return@withContext Result.failure(IllegalStateException("Could not generate summary — chat model may be busy"))

            val chunks = RagChunker.chunk(text)
            if (chunks.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No chunks produced"))
            }

            val ctxPrefix = buildString {
                append("[Document context: ")
                append(doc.name)
                append(" — ")
                append(summary.replace("\n", " ").trim())
                append("]\n\n")
            }

            chunks.forEachIndexed { idx, chunk ->
                val subDocId = "${doc.sourceId}::ctx$idx"
                val combined = ctxPrefix + chunk
                val combinedBytes = combined.toByteArray(Charsets.UTF_8)
                val n = opsMutex.withLock {
                    engine.ingestBytes(combinedBytes, "text/plain", "${doc.name} (ctx$idx)", subDocId)
                }
                if (n >= 0) {
                    ingestedDocIds += subDocId
                    cacheCurrentDoc(subDocId, doc.sourceId)
                }
                keywordIndex.ingest(subDocId, doc.sourceId, listOf(combined))
            }

            documentRepo.getAllDocuments()
                .filter { it.sourceId == doc.sourceId && !it.isDeepIndexed }
                .forEach { documentRepo.updateDocument(it.copy(isDeepIndexed = true, chunkCount = it.chunkCount + chunks.size)) }
            Log.i(TAG, "deepIndex ${doc.name}: ${chunks.size} contextual chunks added")
            Result.success(doc.copy(isDeepIndexed = true, chunkCount = doc.chunkCount + chunks.size))
        } finally {
            _deepIndexing.value = _deepIndexing.value - docMetaId
        }
    }

    suspend fun buildRaptorTree(docMetaId: String): Result<ChatDocument> = raptorMutex.withLock {
        val doc = documentRepo.getDocument(docMetaId)
            ?: return@withLock Result.failure(IllegalStateException("Document not found"))
        if (doc.isRaptorIndexed) return@withLock Result.success(doc)
        if (doc.sourceId.isBlank()) return@withLock Result.failure(IllegalStateException("Document source unavailable"))
        if (!isTextLike(doc.mimeType, doc.name)) {
            return@withLock Result.failure(IllegalStateException("RAPTOR currently supports text-format documents only"))
        }
        if (!ensureReady()) {
            return@withLock Result.failure(IllegalStateException("Embedding model not loaded. Install EmbeddingGemma from Model Store."))
        }

        _raptorBuilding.value = _raptorBuilding.value + docMetaId
        try {
            val bytes = withContext(Dispatchers.IO) { sourceVault.read(doc.sourceId) }
                ?: return@withLock Result.failure(IllegalStateException("Source bytes for ${doc.name} are missing"))
            val text = runCatching { String(bytes, Charsets.UTF_8) }.getOrNull()
                ?: return@withLock Result.failure(IllegalStateException("Could not read source text"))

            val tree = raptor.buildTree(text, doc.name) { _, _, _ -> }
                ?: return@withLock Result.failure(IllegalStateException("Tree build failed (load a chat model and try again)"))

            tree.levels.forEachIndexed { levelIdx, nodes ->
                if (levelIdx == 0) return@forEachIndexed
                nodes.forEachIndexed { clusterIdx, summary ->
                    val subDocId = "${doc.sourceId}::raptor:lvl$levelIdx:cluster$clusterIdx"
                    val summaryBytes = summary.toByteArray(Charsets.UTF_8)
                    val n = opsMutex.withLock {
                        engine.ingestBytes(summaryBytes, "text/plain", "${doc.name} (lvl$levelIdx)", subDocId)
                    }
                    if (n >= 0) {
                        ingestedDocIds += subDocId
                        cacheCurrentDoc(subDocId, doc.sourceId)
                        keywordIndex.ingest(subDocId, doc.sourceId, listOf(summary))
                    } else {
                        Log.w(TAG, "raptor ingest failed for $subDocId: code=$n")
                    }
                }
            }

            documentRepo.getAllDocuments()
                .filter { it.sourceId == doc.sourceId && !it.isRaptorIndexed }
                .forEach { documentRepo.updateDocument(it.copy(isRaptorIndexed = true)) }
            Log.i(TAG, "raptor ${doc.name}: ${tree.levels.size - 1} summary level(s) built")
            Result.success(doc.copy(isRaptorIndexed = true))
        } finally {
            _raptorBuilding.value = _raptorBuilding.value - docMetaId
        }
    }

    suspend fun removeDocument(docMetaId: String) = withContext(Dispatchers.IO) {
        val doc = documentRepo.getAllDocuments().firstOrNull { it.id == docMetaId }
        documentRepo.removeDocument(docMetaId)
        val sourceId = doc?.sourceId
        if (sourceId.isNullOrBlank()) return@withContext

        val stillReferenced = documentRepo.countWithSource(sourceId) > 0
        if (stillReferenced) return@withContext

        opsMutex.withLock { removeSourceFromEngine(sourceId) }
        loadedSources = loadedSources - sourceId
        keywordIndex.removeDocument(sourceId)
        ingestedDocIds.filter { it.startsWith("$sourceId::") }.forEach { sub ->
            keywordIndex.removeDocument(sub)
        }
        runCatching { cache.removeBySource(sourceId) }
            .onFailure { Log.w(TAG, "cache remove failed for $sourceId", it) }
        sourceVault.delete(sourceId)
    }

    suspend fun buildAugmentedPrompt(
        chatId: String,
        query: String,
        originalPrompt: String,
        maxContextTokens: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): String = augment(chatId, query, originalPrompt, maxContextTokens).augmentedPrompt
        .ifEmpty { originalPrompt }

    suspend fun augment(
        chatId: String,
        query: String,
        originalPrompt: String,
        maxContextTokens: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): RagAugmentation = withContext(Dispatchers.IO) {
        if (!_isReady.value) return@withContext RagAugmentation.NONE

        val sourceIds = documentRepo.getDocumentsForChat(chatId)
            .mapNotNull { it.sourceId.takeIf { s -> s.isNotBlank() } }
            .toSet()
        if (sourceIds.isEmpty()) return@withContext RagAugmentation.NONE

        try {
            val variants = if (appPrefs.ragMultiQuery) {
                _retrievalStatus.value = RetrievalStatus.RewritingQuery
                queryRewriter.generateVariants(query)
            } else emptyList()
            val queries = (listOf(query) + variants).distinct()

            _retrievalStatus.value = RetrievalStatus.Searching
            val rankings = mutableListOf<List<Pair<Pair<String, Int>, String>>>()
            var totalDense = 0
            var totalBm25 = 0
            for (q in queries) {
                val dense = opsMutex.withLock { engine.queryFiltered(q, "") }
                    .filter { hit -> hit.docId.parentSourceId() in sourceIds }
                val bm25 = runCatching { keywordIndex.query(q, topK = KEYWORD_CANDIDATES) }
                    .getOrElse { emptyList() }
                    .filter { it.sourceId in sourceIds || it.docId.parentSourceId() in sourceIds }
                totalDense += dense.size
                totalBm25 += bm25.size
                if (dense.isNotEmpty()) rankings += dense.map { (it.docId to it.chunkIndex) to it.text }
                if (bm25.isNotEmpty()) rankings += bm25.map { (it.docId to it.chunkIndex) to it.text }
            }

            if (rankings.isEmpty()) return@withContext RagAugmentation.NONE

            val fused = rrfFuseMany(rankings)
            if (fused.isEmpty()) return@withContext RagAugmentation.NONE

            val chatDocs = documentRepo.getDocumentsForChat(chatId)
            val docsBySourceId = chatDocs.associateBy { it.sourceId }
            val pooled = fused.mapNotNull { hit ->
                val text = hit.text.trim()
                if (text.isEmpty()) return@mapNotNull null
                val parentSource = hit.docId.parentSourceId()
                val doc = docsBySourceId[parentSource]
                RagChunk(
                    docId = parentSource,
                    sourceId = parentSource,
                    chunkIndex = hit.chunkIndex,
                    score = hit.rrfScore,
                    text = text,
                    name = doc?.name ?: "Document",
                    mimeType = doc?.mimeType ?: "application/octet-stream",
                )
            }
            if (pooled.isEmpty()) return@withContext RagAugmentation.NONE

            val ranked = if (appPrefs.ragSmartRerank) {
                _retrievalStatus.value = RetrievalStatus.Reranking
                reranker.rerank(query, pooled)
            } else pooled

            val budget = maxContextTokens.coerceAtLeast(MIN_RAG_BUDGET_TOKENS)
            val kept = mutableListOf<RagChunk>()
            var used = 0
            for (chunk in ranked) {
                val cost = approxTokens(chunk.text) + PER_CHUNK_OVERHEAD_TOKENS
                if (used + cost > budget) {
                    if (kept.isNotEmpty()) break
                    val available = (budget - PER_CHUNK_OVERHEAD_TOKENS).coerceAtLeast(64)
                    val ratio = available.toFloat() / approxTokens(chunk.text)
                    val truncCharCount = (chunk.text.length * ratio).toInt().coerceAtMost(chunk.text.length)
                    if (truncCharCount <= 0) break
                    kept += chunk.copy(text = chunk.text.take(truncCharCount))
                    used += approxTokens(kept.last().text) + PER_CHUNK_OVERHEAD_TOKENS
                    break
                }
                kept += chunk
                used += cost
                if (kept.size >= FINAL_TOP_N) break
            }
            if (kept.isEmpty()) return@withContext RagAugmentation.NONE
            Log.i(TAG, "RAG augment: queries=${queries.size} dense=$totalDense bm25=$totalBm25 fused=${fused.size} pooled=${pooled.size} kept=${kept.size} rerank=${appPrefs.ragSmartRerank} multiQ=${appPrefs.ragMultiQuery} (~$used / $budget tok)")

            val prompt = buildString {
                append("You have access to context retrieved from the user's documents. ")
                append("Write a complete answer to the user's question using your own words, integrating relevant facts from the passages below. ")
                append("Do not respond with only a name, a phrase, or a citation marker — give a full, useful answer of at least one sentence. ")
                append("Cite passages inline using [1], [2], etc. that correspond to the bracketed numbers. ")
                append("If the context is insufficient, fall back to your general knowledge and answer without citing.\n\n")
                append("<context>\n")
                kept.forEachIndexed { index, chunk ->
                    append("[${index + 1}] ")
                    append(chunk.text)
                    append("\n\n")
                }
                append("</context>\n\n")
                append("User question: ")
                append(originalPrompt)
            }
            RagAugmentation(augmentedPrompt = prompt, chunks = kept)
        } finally {
            _retrievalStatus.value = RetrievalStatus.Idle
        }
    }

    private fun String.parentSourceId(): String =
        substringBefore("::")

    private data class FusedHit(
        val docId: String,
        val chunkIndex: Int,
        val text: String,
        val rrfScore: Float,
    )

    private fun rrfFuse(
        dense: List<com.dark.gguf_lib.models.RAGResult>,
        bm25: List<KeywordHit>,
    ): List<FusedHit> {
        val rankings = buildList {
            if (dense.isNotEmpty()) add(dense.map { (it.docId to it.chunkIndex) to it.text })
            if (bm25.isNotEmpty()) add(bm25.map { (it.docId to it.chunkIndex) to it.text })
        }
        return rrfFuseMany(rankings)
    }

    private fun rrfFuseMany(rankings: List<List<Pair<Pair<String, Int>, String>>>): List<FusedHit> {
        if (rankings.isEmpty()) return emptyList()
        val scores = HashMap<Pair<String, Int>, Double>()
        val texts = HashMap<Pair<String, Int>, String>()
        rankings.forEach { ranking ->
            ranking.forEachIndexed { idx, (key, text) ->
                scores.merge(key, 1.0 / (RRF_K + idx + 1)) { a, b -> a + b }
                texts.putIfAbsent(key, text)
            }
        }
        return scores.entries
            .sortedByDescending { it.value }
            .take(FUSED_POOL_SIZE)
            .map { (key, rrf) ->
                FusedHit(
                    docId = key.first,
                    chunkIndex = key.second,
                    text = texts[key].orEmpty(),
                    rrfScore = rrf.toFloat(),
                )
            }
    }

    private fun approxTokens(text: String): Int = (text.length + 3) / 4

    suspend fun debugQuery(
        chatId: String,
        query: String,
        budget: Int = DEFAULT_RAG_BUDGET_TOKENS,
    ): RagDebugResult = withContext(Dispatchers.IO) {
        val info = runCatching { engine.info() }.getOrNull() ?: "{}"
        val sourceIds = documentRepo.getDocumentsForChat(chatId)
            .mapNotNull { it.sourceId.takeIf { s -> s.isNotBlank() } }
            .toSet()
        if (!_isReady.value || query.isBlank() || sourceIds.isEmpty()) {
            return@withContext RagDebugResult(
                query = query,
                isReady = _isReady.value,
                activeChatId = chatId,
                dense = emptyList(),
                bm25 = emptyList(),
                fused = emptyList(),
                contextBlock = "",
                approxContextTokens = 0,
                engineInfo = info,
            )
        }

        val dense = opsMutex.withLock { engine.queryFiltered(query, "") }
            .filter { it.docId.parentSourceId() in sourceIds }
        val bm25 = runCatching { keywordIndex.query(query, KEYWORD_CANDIDATES) }
            .getOrElse { emptyList() }
            .filter { it.sourceId in sourceIds || it.docId.parentSourceId() in sourceIds }
        val fused = rrfFuse(dense, bm25)

        val aug = augment(chatId, query, query, budget)
        RagDebugResult(
            query = query,
            isReady = true,
            activeChatId = chatId,
            dense = dense.map { RagDebugHit(it.docId, it.chunkIndex, it.score, it.text) },
            bm25 = bm25.map { RagDebugHit(it.docId, it.chunkIndex, it.rank.toFloat(), it.text) },
            fused = fused.map { RagDebugHit(it.docId, it.chunkIndex, it.rrfScore, it.text) },
            contextBlock = if (aug.didAugment) aug.augmentedPrompt.removeSuffix(query).trim() else "",
            approxContextTokens = if (aug.didAugment) approxTokens(aug.augmentedPrompt) else 0,
            engineInfo = info,
        )
    }

    suspend fun release() = opsMutex.withLock {
        engine.close()
        ingestedDocIds.clear()
        loadedSources = emptySet()
        _isReady.value = false
        _activeEmbeddingName.value = null
    }

    private fun indexKeywordsIfTextLike(
        sourceId: String,
        name: String,
        mime: String,
        bytes: ByteArray,
    ) {
        if (!isTextLike(mime, name)) return
        if (keywordIndex.docCount(sourceId) > 0) return
        val text = runCatching { bytes.toString(Charsets.UTF_8) }.getOrNull()?.takeIf { it.isNotBlank() } ?: return
        if (text.contains('�') && text.count { it == '�' }.toFloat() / text.length > 0.05f) return
        val chunks = RagChunker.chunk(text)
        if (chunks.isEmpty()) return
        val n = keywordIndex.ingest(sourceId, sourceId, chunks)
        Log.i(TAG, "BM25 indexed $n chunks for $sourceId (mime=$mime)")
    }

    private fun isTextLike(mime: String?, name: String?): Boolean {
        val m = mime?.lowercase()?.substringBefore(';')?.trim()
        if (!m.isNullOrBlank()) {
            if (m.startsWith("text/")) return true
            if (m in TEXT_LIKE_MIMES) return true
        }
        val ext = name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()
        if (!ext.isNullOrBlank() && ext in TEXT_LIKE_EXTS) return true
        return false
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
        const val DEFAULT_RAG_BUDGET_TOKENS = 1536
        const val MIN_RAG_BUDGET_TOKENS = 256
        const val PER_CHUNK_OVERHEAD_TOKENS = 8
        const val DENSE_CANDIDATES = 20
        const val KEYWORD_CANDIDATES = 20
        const val FUSED_POOL_SIZE = 12
        const val FINAL_TOP_N = 8
        const val RRF_K = 60
        val TEXT_LIKE_MIMES = setOf(
            "application/json",
            "application/xml",
            "application/rtf",
            "application/x-rtf",
            "application/javascript",
            "application/x-yaml",
            "application/yaml",
        )
        val TEXT_LIKE_EXTS = setOf(
            "txt", "md", "markdown", "json", "xml", "csv", "tsv", "html", "htm", "rtf",
            "yaml", "yml", "log", "ini", "toml", "properties", "kt", "java", "py", "js", "ts",
        )
    }
}

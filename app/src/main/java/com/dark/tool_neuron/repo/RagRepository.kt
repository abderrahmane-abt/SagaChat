package com.dark.tool_neuron.repo

import android.content.Context
import android.net.Uri
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagSourceType
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.neuron_example.NeuronGraph
import com.dark.tool_neuron.neuron_example.NeuronNode
import com.neuronpacket.NeuronPacketManager
import com.neuronpacket.PacketMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

class RagRepository(
    private val ragDao: RagDao,
    private val context: Context
) {
    private val ragsDir: File by lazy {
        File(context.filesDir, "rags").also { it.mkdirs() }
    }

    // Loaded graphs in memory
    private val loadedGraphs = mutableMapOf<String, NeuronGraph>()

    // ==================== Database Operations ====================

    fun getAllRags(): Flow<List<InstalledRag>> = ragDao.getAllRags()

    suspend fun getAllRagsOnce(): List<InstalledRag> = ragDao.getAllRagsOnce()

    fun getLoadedRags(): Flow<List<InstalledRag>> = ragDao.getLoadedRags()

    fun getEnabledRags(): Flow<List<InstalledRag>> = ragDao.getEnabledRags()

    suspend fun getEnabledRagsOnce(): List<InstalledRag> = ragDao.getEnabledRagsOnce()

    suspend fun getRagById(id: String): InstalledRag? = ragDao.getById(id)

    suspend fun insertRag(rag: InstalledRag) = ragDao.insert(rag)

    suspend fun updateRag(rag: InstalledRag) = ragDao.update(rag)

    suspend fun deleteRag(id: String) {
        ragDao.deleteById(id)
        // Also delete the file
        val ragFile = File(ragsDir, "$id.neuron")
        if (ragFile.exists()) {
            ragFile.delete()
        }
        // Remove from loaded graphs
        loadedGraphs.remove(id)
    }

    suspend fun updateRagStatus(id: String, status: RagStatus) = ragDao.updateStatus(id, status)

    suspend fun updateRagEnabled(id: String, isEnabled: Boolean) = ragDao.updateEnabled(id, isEnabled)

    suspend fun markRagAsLoaded(id: String) = ragDao.markAsLoaded(id)

    suspend fun markRagAsUnloaded(id: String) = ragDao.markAsUnloaded(id)

    suspend fun unloadAllRags() {
        ragDao.unloadAllRags()
        loadedGraphs.clear()
    }

    /**
     * Sync database state with in-memory state on app startup.
     * Since loadedGraphs is empty after app restart, mark all RAGs as unloaded
     * so the UI correctly reflects that no RAGs are loaded.
     */
    suspend fun syncLoadedStateOnStartup() = withContext(Dispatchers.IO) {
        // Mark all RAGs that were marked as LOADED in DB as INSTALLED
        // since we don't have them in memory anymore
        ragDao.unloadAllRags()
    }

    suspend fun getRagCount(): Int = ragDao.getRagCount()

    suspend fun getLoadedRagCount(): Int = ragDao.getLoadedRagCount()

    suspend fun searchRags(query: String): List<InstalledRag> = ragDao.searchRags(query)

    // ==================== File Operations ====================

    suspend fun installRagFromUri(uri: Uri, name: String? = null): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))

            // Generate unique ID
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            // Copy file to app directory
            inputStream.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            // Try to read metadata from the packet
            val packetManager = NeuronPacketManager()
            val openResult = packetManager.open(destFile)

            val ragInfo = if (openResult.isSuccess) {
                val info = packetManager.getPacketInfo()
                val metadata = parseMetadataJson(info?.metadataJson)
                packetManager.close()

                InstalledRag(
                    id = ragId,
                    name = name ?: metadata?.optString("name") ?: "Imported RAG",
                    description = metadata?.optString("description") ?: "",
                    sourceType = RagSourceType.NEURON_PACKET,
                    filePath = destFile.absolutePath,
                    nodeCount = metadata?.optInt("nodeCount") ?: 0,
                    embeddingDimension = metadata?.optInt("embeddingDimension") ?: 0,
                    embeddingModel = metadata?.optString("embeddingModel") ?: "",
                    domain = metadata?.optString("domain") ?: "general",
                    language = metadata?.optString("language") ?: "en",
                    version = metadata?.optString("version") ?: "1.0",
                    tags = metadata?.optString("tags") ?: "",
                    status = RagStatus.INSTALLED,
                    sizeBytes = destFile.length()
                )
            } else {
                InstalledRag(
                    id = ragId,
                    name = name ?: "Imported RAG",
                    sourceType = RagSourceType.NEURON_PACKET,
                    filePath = destFile.absolutePath,
                    status = RagStatus.INSTALLED,
                    sizeBytes = destFile.length()
                )
            }

            ragDao.insert(ragInfo)
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRagFromText(
        name: String,
        description: String,
        text: String,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val addResult = graph.addText(text, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add text"))
            }

            val nodes = addResult.getOrThrow()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            // Serialize and save
            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.TEXT,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = nodes.firstOrNull()?.embedding?.size ?: 0,
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.LOADED,  // Set as LOADED since graph is in memory
                isEnabled = true,  // Enable by default for immediate use
                sizeBytes = destFile.length()
            )

            ragDao.insert(ragInfo)
            loadedGraphs[ragId] = graph
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRagFromChat(
        name: String,
        description: String,
        chatId: String,
        messages: List<com.dark.tool_neuron.models.messages.Messages>,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val addResult = graph.addChatMessages(messages, chatId, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add chat"))
            }

            val nodes = addResult.getOrThrow()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.CHAT,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = nodes.firstOrNull()?.embedding?.size ?: 0,
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.LOADED,  // Set as LOADED since graph is in memory
                isEnabled = true,  // Enable by default for immediate use
                sizeBytes = destFile.length()
            )

            ragDao.insert(ragInfo)
            loadedGraphs[ragId] = graph
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun createRagFromFile(
        name: String,
        description: String,
        fileUri: Uri,
        graph: NeuronGraph,
        domain: String = "general",
        tags: List<String> = emptyList()
    ): Result<InstalledRag> = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.contentResolver.openInputStream(fileUri)
                ?: return@withContext Result.failure(Exception("Cannot open file"))

            val content = inputStream.bufferedReader().use { it.readText() }

            val addResult = graph.addText(content, name)
            if (addResult.isFailure) {
                return@withContext Result.failure(addResult.exceptionOrNull() ?: Exception("Failed to add file content"))
            }

            val nodes = addResult.getOrThrow()
            val ragId = java.util.UUID.randomUUID().toString()
            val destFile = File(ragsDir, "$ragId.neuron")

            val payload = graph.serialize()
            destFile.writeBytes(payload)

            val ragInfo = InstalledRag(
                id = ragId,
                name = name,
                description = description,
                sourceType = RagSourceType.FILE,
                filePath = destFile.absolutePath,
                nodeCount = nodes.size,
                embeddingDimension = nodes.firstOrNull()?.embedding?.size ?: 0,
                domain = domain,
                tags = tags.joinToString(","),
                status = RagStatus.LOADED,  // Set as LOADED since graph is in memory
                isEnabled = true,  // Enable by default for immediate use
                sizeBytes = destFile.length()
            )

            ragDao.insert(ragInfo)
            loadedGraphs[ragId] = graph
            Result.success(ragInfo)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ==================== Graph Operations ====================

    fun getLoadedGraph(ragId: String): NeuronGraph? = loadedGraphs[ragId]

    fun isGraphLoaded(ragId: String): Boolean = loadedGraphs.containsKey(ragId)

    suspend fun loadGraph(ragId: String, graph: NeuronGraph, password: String? = null): Result<NeuronGraph> = withContext(Dispatchers.IO) {
        try {
            val rag = ragDao.getById(ragId) ?: return@withContext Result.failure(Exception("RAG not found"))
            val file = File(rag.filePath ?: return@withContext Result.failure(Exception("RAG file path is null")))

            if (!file.exists()) {
                return@withContext Result.failure(Exception("RAG file not found"))
            }

            // For neuron packets, we need to decrypt
            if (rag.sourceType == RagSourceType.NEURON_PACKET && password != null) {
                val packetManager = NeuronPacketManager()
                packetManager.open(file)
                val authResult = packetManager.authenticate(password)
                if (authResult.isFailure) {
                    return@withContext Result.failure(authResult.exceptionOrNull() ?: Exception("Authentication failed"))
                }
                val payloadResult = packetManager.decryptPayload(authResult.getOrThrow())
                if (payloadResult.isFailure) {
                    return@withContext Result.failure(payloadResult.exceptionOrNull() ?: Exception("Decryption failed"))
                }
                val deserializeResult = graph.deserialize(payloadResult.getOrThrow())
                if (deserializeResult.isFailure) {
                    return@withContext Result.failure(deserializeResult.exceptionOrNull() ?: Exception("Deserialization failed"))
                }
                packetManager.close()
            } else {
                // Direct file read
                val payload = file.readBytes()
                val deserializeResult = graph.deserialize(payload)
                if (deserializeResult.isFailure) {
                    return@withContext Result.failure(deserializeResult.exceptionOrNull() ?: Exception("Deserialization failed"))
                }
            }

            loadedGraphs[ragId] = graph
            ragDao.markAsLoaded(ragId)
            // Also enable the RAG when loaded so it can be queried
            ragDao.updateEnabled(ragId, true)
            Result.success(graph)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unloadGraph(ragId: String) {
        loadedGraphs.remove(ragId)
        ragDao.markAsUnloaded(ragId)
    }

    // ==================== Query Operations ====================

    suspend fun queryAllLoadedGraphs(
        query: String,
        topK: Int = 5
    ): List<Pair<InstalledRag, List<com.dark.tool_neuron.neuron_example.QueryResult>>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<InstalledRag, List<com.dark.tool_neuron.neuron_example.QueryResult>>>()

        for ((ragId, graph) in loadedGraphs) {
            val rag = ragDao.getById(ragId) ?: continue
            if (!rag.isEnabled) continue

            val queryResults = graph.query(query, topK)
            if (queryResults.isNotEmpty()) {
                results.add(rag to queryResults)
            }
        }

        results
    }

    // ==================== Utility ====================

    private fun parseMetadataJson(json: String?): JSONObject? {
        return try {
            json?.let { JSONObject(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun getRagsDirectory(): File = ragsDir
}
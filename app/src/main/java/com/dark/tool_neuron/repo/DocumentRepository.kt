package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.tool_neuron.model.ChatDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val storage = HexStorage()

    init {
        val dir = File(context.filesDir, "chat_documents")
        dir.mkdirs()
        val path = dir.absolutePath
        if (storage.exists(path)) {
            storage.openPlaintext(path)
        } else {
            storage.createPlaintext(path)
        }
        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COLLECTION, TAG_CHAT_ID, HexStorage.WIRE_BYTES)
    }

    fun getDocumentsForChat(chatId: String): List<ChatDocument> {
        return storage.queryString(COLLECTION, TAG_CHAT_ID, chatId).map { it.toChatDocument() }
    }

    fun getAllDocuments(): List<ChatDocument> {
        return storage.getAll(COLLECTION).map { it.toChatDocument() }
    }

    fun addDocument(doc: ChatDocument) {
        storage.queryString(COLLECTION, TAG_ID, doc.id).forEach {
            storage.delete(COLLECTION, it.id)
        }
        storage.put(COLLECTION, doc.toRecord())
        storage.flush(COLLECTION)
    }

    fun removeDocument(docId: String) {
        val records = storage.queryString(COLLECTION, TAG_ID, docId)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun removeAllForChat(chatId: String) {
        val records = storage.queryString(COLLECTION, TAG_CHAT_ID, chatId)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun clearAll() {
        val records = storage.getAll(COLLECTION)
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun countWithSource(sourceId: String): Int {
        if (sourceId.isBlank()) return 0
        return storage.getAll(COLLECTION).count { rec ->
            rec.getString(TAG_SOURCE_ID) == sourceId
        }
    }

    companion object {
        private const val COLLECTION = "chat_documents"

        private const val TAG_ID = 1
        private const val TAG_CHAT_ID = 2
        private const val TAG_NAME = 3
        private const val TAG_MIME_TYPE = 4
        private const val TAG_CHUNK_COUNT = 5
        private const val TAG_SIZE_BYTES = 6
        private const val TAG_ADDED_AT = 7
        private const val TAG_SOURCE_ID = 8
    }

    private fun ChatDocument.toRecord(): HxsRecord {
        val d = this
        return HxsRecord.build {
            putString(TAG_ID, d.id)
            putString(TAG_CHAT_ID, d.chatId ?: "")
            putString(TAG_NAME, d.name)
            putString(TAG_MIME_TYPE, d.mimeType)
            putTimestamp(TAG_CHUNK_COUNT, d.chunkCount.toLong())
            putTimestamp(TAG_SIZE_BYTES, d.sizeBytes)
            putTimestamp(TAG_ADDED_AT, d.addedAt)
            putString(TAG_SOURCE_ID, d.sourceId)
        }
    }

    private fun HxsRecord.toChatDocument(): ChatDocument = ChatDocument(
        id = getString(TAG_ID),
        chatId = getString(TAG_CHAT_ID),
        name = getString(TAG_NAME),
        mimeType = getString(TAG_MIME_TYPE),
        chunkCount = getTimestamp(TAG_CHUNK_COUNT).toInt(),
        sizeBytes = getTimestamp(TAG_SIZE_BYTES),
        addedAt = getTimestamp(TAG_ADDED_AT),
        sourceId = getString(TAG_SOURCE_ID),
    )
}

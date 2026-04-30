package com.dark.tool_neuron.repo

import android.content.Context
import android.util.Log
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class RagCacheEntry(
    val docId: String,
    val sourceId: String,
    val fingerprint: String,
    val blob: ByteArray,
)

@Singleton
class RagCacheRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    init {
        val secureDir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val secureBase = secureDir.absolutePath

        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)

        if (!openOrRebuild(secureBase, dek, userKey)) {
            throw SecurityException("Failed to open encrypted rag_cache vault")
        }

        storage.ensureCollection(COLLECTION)
        storage.addIndex(COLLECTION, TAG_DOC_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COLLECTION, TAG_SOURCE_ID, HexStorage.WIRE_BYTES)
        storage.addIndex(COLLECTION, TAG_FINGERPRINT, HexStorage.WIRE_BYTES)

        runCatching { migrateLegacyPlaintextSnapshot() }
            .onFailure { Log.w(TAG, "legacy snapshot cleanup failed", it) }
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    private fun migrateLegacyPlaintextSnapshot() {
        val legacy = File(context.filesDir, LEGACY_SNAPSHOT_FILE)
        if (legacy.exists()) {
            legacy.delete()
            Log.i(TAG, "deleted legacy plaintext rag snapshot")
        }
    }

    fun read(docId: String): RagCacheEntry? {
        val rec = storage.queryString(COLLECTION, TAG_DOC_ID, docId).firstOrNull() ?: return null
        return rec.toEntry()
    }

    fun readBySource(sourceId: String): List<RagCacheEntry> {
        if (sourceId.isBlank()) return emptyList()
        return storage.queryString(COLLECTION, TAG_SOURCE_ID, sourceId).map { it.toEntry() }
    }

    fun write(entry: RagCacheEntry) {
        storage.queryString(COLLECTION, TAG_DOC_ID, entry.docId).forEach {
            storage.delete(COLLECTION, it.id)
        }
        storage.put(COLLECTION, entry.toRecord())
        storage.flush(COLLECTION)
    }

    fun removeBySource(sourceId: String) {
        if (sourceId.isBlank()) return
        val records = storage.queryString(COLLECTION, TAG_SOURCE_ID, sourceId)
        if (records.isEmpty()) return
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun removeDoc(docId: String) {
        val records = storage.queryString(COLLECTION, TAG_DOC_ID, docId)
        if (records.isEmpty()) return
        records.forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun invalidateForeign(activeFingerprint: String) {
        if (activeFingerprint.isBlank()) return
        val all = storage.getAll(COLLECTION)
        var dropped = 0
        all.forEach { rec ->
            val fp = rec.getString(TAG_FINGERPRINT, "")
            if (fp != activeFingerprint) {
                storage.delete(COLLECTION, rec.id)
                dropped++
            }
        }
        if (dropped > 0) {
            storage.flush(COLLECTION)
            Log.i(TAG, "rag cache: dropped $dropped stale entries (fp swap)")
        }
    }

    fun clearAll() {
        storage.getAll(COLLECTION).forEach { storage.delete(COLLECTION, it.id) }
        storage.flush(COLLECTION)
    }

    fun count(): Int = storage.count(COLLECTION)

    private fun RagCacheEntry.toRecord(): HxsRecord = HxsRecord.build {
        putString(TAG_DOC_ID, docId)
        putString(TAG_SOURCE_ID, sourceId)
        putString(TAG_FINGERPRINT, fingerprint)
        putBytes(TAG_BLOB, blob)
    }

    private fun HxsRecord.toEntry(): RagCacheEntry = RagCacheEntry(
        docId = getString(TAG_DOC_ID),
        sourceId = getString(TAG_SOURCE_ID),
        fingerprint = getString(TAG_FINGERPRINT),
        blob = getBytes(TAG_BLOB) ?: ByteArray(0),
    )

    companion object {
        private const val TAG = "RagCacheRepository"
        private const val SECURE_DIR = "rag_cache_v1"
        private const val LEGACY_SNAPSHOT_FILE = "rag_vector_snapshot_v1.bin"
        private const val COLLECTION = "rag_doc_blobs"
        private const val USER_KEY_INFO = "tn.rag_cache.user_key.v2"

        private const val TAG_DOC_ID = 1
        private const val TAG_SOURCE_ID = 2
        private const val TAG_FINGERPRINT = 3
        private const val TAG_BLOB = 4
    }
}

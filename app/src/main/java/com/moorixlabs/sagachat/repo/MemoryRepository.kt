package com.moorixlabs.sagachat.repo

import android.content.Context
import com.moorixlabs.hxs.HexStorage
import com.moorixlabs.hxs.HxsRecord
import com.moorixlabs.hxs_encryptor.HxsEncryptor
import com.moorixlabs.sagachat.data.AppKeyStore
import com.moorixlabs.sagachat.model.MemoryState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val path = dir.absolutePath
        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)
        val opened = openOrRebuild(path, dek, userKey)
        if (!opened) throw SecurityException("Failed to open memory_store vault")
        storage.ensureCollection(COL_MEMORY)
        storage.addIndex(COL_MEMORY, TAG_CHARACTER_ID, HexStorage.WIRE_BYTES)
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    fun get(characterId: String): MemoryState =
        storage.queryString(COL_MEMORY, TAG_CHARACTER_ID, characterId)
            .firstOrNull()?.toMemoryState()
            ?: MemoryState(characterId = characterId)

    fun save(state: MemoryState) {
        storage.queryString(COL_MEMORY, TAG_CHARACTER_ID, state.characterId)
            .forEach { storage.delete(COL_MEMORY, it.id) }
        storage.put(COL_MEMORY, state.toRecord())
        storage.flush(COL_MEMORY)
    }

    fun delete(characterId: String) {
        storage.queryString(COL_MEMORY, TAG_CHARACTER_ID, characterId)
            .forEach { storage.delete(COL_MEMORY, it.id) }
        storage.flush(COL_MEMORY)
    }

    companion object {
        private const val SECURE_DIR = "memory_store"
        private const val USER_KEY_INFO = "tn.memory.user_key.v1"
        private const val COL_MEMORY = "memory"

        private const val TAG_CHARACTER_ID          = 1
        private const val TAG_SUMMARY               = 2
        private const val TAG_ENTITY_JSON           = 3
        private const val TAG_LAST_SUMMARIZED_INDEX = 4
        private const val TAG_UPDATED_AT            = 5
    }

    private fun MemoryState.toRecord(): HxsRecord {
        val m = this
        return HxsRecord.build {
            putString(TAG_CHARACTER_ID, m.characterId)
            putString(TAG_SUMMARY, m.summary)
            putString(TAG_ENTITY_JSON, m.entityJson)
            putTimestamp(TAG_LAST_SUMMARIZED_INDEX, m.lastSummarizedTurnIndex.toLong())
            putTimestamp(TAG_UPDATED_AT, m.updatedAt)
        }
    }

    private fun HxsRecord.toMemoryState(): MemoryState = MemoryState(
        characterId             = getString(TAG_CHARACTER_ID),
        summary                 = getString(TAG_SUMMARY),
        entityJson              = getString(TAG_ENTITY_JSON).ifEmpty { "{}" },
        lastSummarizedTurnIndex = getTimestamp(TAG_LAST_SUMMARIZED_INDEX).toInt(),
        updatedAt               = getTimestamp(TAG_UPDATED_AT),
    )
}

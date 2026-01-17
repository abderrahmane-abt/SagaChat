package com.memoryvault.core

import android.util.Log
import com.memoryvault.BackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore

class MigrationManager(
    private val vaultFile: VaultFile,
    private val reader: BlockReader,
    private val vaultDir: File
) {
    companion object {
        private const val TAG = "MigrationManager"
        private const val OLD_KEY_ALIAS = "memory_vault_master_key"
    }

    suspend fun migrate(
        newKeyAlias: String,
        onProgress: (Float) -> Unit
    ): MigrationResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "Starting encryption key migration")

        var backupFile: File? = null

        try {
            // Phase 1: Create backup
            Log.d(TAG, "Phase 1: Creating backup")
            backupFile = File(vaultDir, "vault_pre_migration_${System.currentTimeMillis()}.mvlt")
            val backupManager = BackupManager(vaultFile.file)
            val backupResult = backupManager.backup(backupFile, compress = false)

            if (!backupResult.success) {
                return@withContext MigrationResult.Failure(
                    error = Exception("Backup failed: ${backupResult.message}"),
                    backupLocation = null
                )
            }

            Log.d(TAG, "Backup created at: ${backupFile.absolutePath}")

            // Phase 2: Create encryption managers
            Log.d(TAG, "Phase 2: Setting up encryption managers")
            val oldEM = EncryptionManager(OLD_KEY_ALIAS)
            val newEM = EncryptionManager(newKeyAlias)

            // Phase 3: Load current header and index with old key
            Log.d(TAG, "Phase 3: Loading current vault state")
            val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
            val header = VaultHeader.fromBytes(headerBytes)

            if (header.keyVersion.toInt() == 1) {
                Log.d(TAG, "Vault already migrated to new key")
                return@withContext MigrationResult.Success(
                    blocksReEncrypted = 0,
                    backupLocation = backupFile.absolutePath
                )
            }

            val metadata = if (header.indexOffset > 0 && header.indexSize > 0) {
                val encryptedIndexData = vaultFile.readAt(header.indexOffset, header.indexSize.toInt())
                val oldEncryptedData = EncryptedData.fromBytes(encryptedIndexData)
                val decryptedIndexData = oldEM.decrypt(oldEncryptedData)
                IndexSerializer.deserialize(decryptedIndexData)
            } else {
                emptyList()
            }

            Log.d(TAG, "Loaded ${metadata.size} blocks from index")

            // Phase 4: Re-encrypt each block
            Log.d(TAG, "Phase 4: Re-encrypting blocks")
            val totalBlocks = metadata.size

            metadata.forEachIndexed { index, meta ->
                try {
                    // Read block
                    val block = reader.readBlock(meta.fileOffset)

                    // Only re-encrypt if the block is encrypted
                    if (block.header.encryptionFlag) {
                        // Decrypt with old key
                        val oldData = EncryptedData.fromBytes(block.data)
                        val decrypted = oldEM.decrypt(oldData)

                        // Re-encrypt with new key
                        val newEncrypted = newEM.encrypt(decrypted)
                        val newData = newEncrypted.toBytes()

                        // Write back at same offset (data portion only, not header)
                        vaultFile.writeAt(meta.fileOffset + BlockHeader.HEADER_SIZE, newData)

                        // Update block header to reflect new size if changed
                        val newHeader = block.header.copy(
                            contentSize = newData.size.toLong(),
                            checksum = BlockHeader.calculateChecksum(newData)
                        )
                        vaultFile.writeAt(meta.fileOffset, newHeader.toBytes())
                    }

                    // Update progress
                    onProgress((index + 1).toFloat() / totalBlocks)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to re-encrypt block ${meta.blockId}", e)
                    throw Exception("Failed to re-encrypt block ${meta.blockId}: ${e.message}", e)
                }
            }

            // Phase 5: Re-encrypt index with new key
            Log.d(TAG, "Phase 5: Re-encrypting index")
            val newIndexData = IndexSerializer.serialize(metadata)
            val newEncryptedIndex = newEM.encrypt(newIndexData)
            val newIndexBytes = newEncryptedIndex.toBytes()

            val newIndexOffset = vaultFile.size()
            vaultFile.writeAt(newIndexOffset, newIndexBytes)

            // Phase 6: Update header with keyVersion = 1
            Log.d(TAG, "Phase 6: Updating vault header")
            val newHeader = header.copy(
                keyVersion = 1,
                indexOffset = newIndexOffset,
                indexSize = newIndexBytes.size.toLong(),
                modifiedTime = System.currentTimeMillis()
            )
            vaultFile.writeAt(0, newHeader.toBytes())

            // Phase 7: Delete old key from Keystore
            Log.d(TAG, "Phase 7: Cleaning up old key")
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (keyStore.containsAlias(OLD_KEY_ALIAS)) {
                keyStore.deleteEntry(OLD_KEY_ALIAS)
                Log.d(TAG, "Old key deleted from Keystore")
            }

            Log.d(TAG, "Migration completed successfully")
            MigrationResult.Success(
                blocksReEncrypted = totalBlocks,
                backupLocation = backupFile.absolutePath
            )

        } catch (e: Exception) {
            Log.e(TAG, "Migration failed", e)
            MigrationResult.Failure(
                error = e,
                backupLocation = backupFile?.absolutePath
            )
        }
    }

    suspend fun needsMigration(): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!vaultFile.exists() || vaultFile.size() == 0L) {
                return@withContext false
            }

            val headerBytes = vaultFile.readAt(0, VaultHeader.HEADER_SIZE)
            val header = VaultHeader.fromBytes(headerBytes)

            // Need migration if keyVersion is 0 (old key)
            header.keyVersion.toInt() == 0
        } catch (e: Exception) {
            Log.e(TAG, "Error checking migration status", e)
            false
        }
    }
}

package com.neuronpacket

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

class NeuronPacketManager {
    private val native = NeuronPacketNative()
    private val mutex = Mutex()
    private val sessionCache = mutableMapOf<String, PacketSession>()

    val version: String get() = native.getVersion()
    val isOpen: Boolean get() = native.isOpen()

    suspend fun export(
        outputFile: File,
        metadata: PacketMetadata,
        payload: ByteArray,
        config: ExportConfig
    ): Result<ExportResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val userPasswords = config.readOnlyUsers.map { it.password }.toTypedArray()
                val userLabels = config.readOnlyUsers.map { it.label }.toTypedArray()

                val result = native.exportPacket(
                    outputFile.absolutePath,
                    metadata.name,
                    metadata.domain,
                    payload,
                    config.adminPassword,
                    config.loadingMode.value,
                    if (userPasswords.isEmpty()) null else userPasswords,
                    if (userLabels.isEmpty()) null else userLabels
                )

                if (result.success) {
                    Result.success(result)
                } else {
                    Result.failure(NeuronPacketException(result.errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun open(packetFile: File): Result<ImportResult> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val result = native.openPacket(packetFile.absolutePath)
                if (result.success) {
                    Result.success(result)
                } else {
                    Result.failure(NeuronPacketException(result.errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun authenticate(password: String): Result<PacketSession> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                if (!native.isOpen()) {
                    return@withContext Result.failure(NeuronPacketException("No packet open"))
                }

                val result = native.authenticate(password)
                if (result.success) {
                    val session = PacketSession(
                        packetId = native.getPacketId(),
                        permissions = Permission.fromValue(result.permissions),
                        loadingMode = LoadingMode.fromValue(native.getLoadingMode()),
                        dek = result.dek
                    )
                    sessionCache[session.packetId] = session
                    Result.success(session)
                } else {
                    Result.failure(NeuronPacketException(result.errorMessage))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun decryptPayload(session: PacketSession): Result<ByteArray> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val dek = session.dek ?: return@withContext Result.failure(
                    NeuronPacketException("No decryption key available")
                )
                val payload = native.decryptPayload(dek)
                    ?: return@withContext Result.failure(NeuronPacketException("Decryption failed"))
                Result.success(payload)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun close() = withContext(Dispatchers.IO) {
        mutex.withLock {
            native.closePacket()
            sessionCache.clear()
        }
    }

    suspend fun addUser(
        credentials: UserCredentials,
        adminPassword: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val success = native.addUser(
                    credentials.password,
                    credentials.label,
                    credentials.permissions.value,
                    adminPassword
                )
                if (success) Result.success(true)
                else Result.failure(NeuronPacketException("Failed to add user"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun removeUser(slotId: Int, adminPassword: String): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val success = native.removeUser(slotId, adminPassword)
                if (success) Result.success(true)
                else Result.failure(NeuronPacketException("Failed to remove user"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun changePassword(
        slotId: Int,
        oldPassword: String,
        newPassword: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val success = native.changePassword(slotId, oldPassword, newPassword)
                if (success) Result.success(true)
                else Result.failure(NeuronPacketException("Failed to change password"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun resetAdminPassword(
        recoveryKey: String,
        newPassword: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        mutex.withLock {
            try {
                val success = native.resetAdminPassword(recoveryKey, newPassword)
                if (success) Result.success(true)
                else Result.failure(NeuronPacketException("Invalid recovery key"))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    fun getPacketInfo(): PacketInfo? {
        if (!native.isOpen()) return null
        return PacketInfo(
            packetId = native.getPacketId(),
            userCount = native.getUserCount(),
            loadingMode = LoadingMode.fromValue(native.getLoadingMode()),
            metadataJson = native.getMetadataJson()
        )
    }

    fun getCachedSession(packetId: String): PacketSession? = sessionCache[packetId]

    fun clearSessionCache() {
        sessionCache.clear()
    }
}

data class PacketInfo(
    val packetId: String,
    val userCount: Int,
    val loadingMode: LoadingMode,
    val metadataJson: String
)

class NeuronPacketException(message: String) : Exception(message)
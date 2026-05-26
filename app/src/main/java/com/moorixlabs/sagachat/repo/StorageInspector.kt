package com.moorixlabs.sagachat.repo

import android.content.Context
import com.moorixlabs.sagachat.model.enums.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class StorageCategoryId {
    CHAT_MODELS,
    CHATS,
    CACHE,
    SYSTEM,
}

data class StorageCategorySnapshot(
    val id: StorageCategoryId,
    val sizeBytes: Long,
    val itemCount: Int,
)

data class StorageSnapshot(
    val totalBytes: Long,
    val categories: Map<StorageCategoryId, StorageCategorySnapshot>,
)

@Singleton
class StorageInspector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val chatRepo: ChatRepository,
) {
    suspend fun snapshot(): StorageSnapshot = withContext(Dispatchers.IO) {
        val files = context.filesDir
        val cache = context.cacheDir

        val modelsDir = File(files, "models")
        val chatStoreDir = File(files, "chat_store_v2")

        val chatModelsBytes = dirSize(modelsDir)
        val chatsBytes = dirSize(chatStoreDir)
        val cacheBytes = dirSize(cache)

        val installed = modelRepo.models.value
        val chatModelCount = installed.count { it.providerType == ProviderType.GGUF }
        val chatCount = chatRepo.chats.value.size

        val systemDirs = listOf(
            File(files, "app_bootstrap"),
            File(files, "app_prefs"),
            File(files, "config"),
            File(files, "model_store"),
        )
        val systemBytes = systemDirs.sumOf { dirSize(it) }

        val total = chatModelsBytes + chatsBytes + cacheBytes + systemBytes

        val map = mapOf(
            StorageCategoryId.CHAT_MODELS to StorageCategorySnapshot(
                StorageCategoryId.CHAT_MODELS, chatModelsBytes, chatModelCount,
            ),
            StorageCategoryId.CHATS to StorageCategorySnapshot(
                StorageCategoryId.CHATS, chatsBytes, chatCount,
            ),
            StorageCategoryId.CACHE to StorageCategorySnapshot(
                StorageCategoryId.CACHE, cacheBytes, 0,
            ),
            StorageCategoryId.SYSTEM to StorageCategorySnapshot(
                StorageCategoryId.SYSTEM, systemBytes, systemDirs.count { it.exists() },
            ),
        )

        StorageSnapshot(totalBytes = total, categories = map)
    }

    suspend fun clear(category: StorageCategoryId) = withContext(Dispatchers.IO) {
        when (category) {
            StorageCategoryId.CHAT_MODELS -> clearChatModels()
            StorageCategoryId.CHATS -> clearChats()
            StorageCategoryId.CACHE -> clearCache()
            StorageCategoryId.SYSTEM -> Unit
        }
    }

    private fun clearChatModels() {
        val models = modelRepo.models.value.filter { it.providerType == ProviderType.GGUF }
        models.forEach { model ->
            val file = File(model.path)
            if (file.exists()) file.delete()
            modelRepo.delete(model.id)
        }
    }

    private fun clearChats() {
        val chats = chatRepo.chats.value.toList()
        chats.forEach { chatRepo.deleteChat(it.id) }
    }

    private fun clearCache() {
        context.cacheDir.deleteRecursively()
        context.cacheDir.mkdirs()
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                total += f.length()
            }
        }
        return total
    }
}

package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.database.dao.AiMemoryDao
import com.dark.tool_neuron.database.dao.ModelDao
import com.dark.tool_neuron.database.dao.PersonaDao
import com.dark.tool_neuron.database.dao.RagDao
import com.dark.tool_neuron.models.enums.PathType
import com.dark.tool_neuron.repo.RagRepository
import com.dark.tool_neuron.vault.VaultHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Runs once on app startup to detect and fix data integrity issues.
 * Non-destructive for valid data.
 */
class DataIntegrityManager(
    private val context: Context,
    private val modelDao: ModelDao,
    private val personaDao: PersonaDao,
    private val ragDao: RagDao,
    private val aiMemoryDao: AiMemoryDao,
    private val ragRepository: RagRepository,
    private val appSettings: AppSettingsDataStore
) {

    companion object {
        private const val TAG = "DataIntegrityManager"
        private const val AI_MEMORY_CAP = 500
    }

    data class IntegrityReport(
        val danglingChatIdCleared: Boolean = false,
        val danglingPersonaIdCleared: Boolean = false,
        val danglingModelIdCleared: Boolean = false,
        val modelsDeactivated: Int = 0,
        val orphanedRagsDeleted: Int = 0,
        val orphanedAvatarsDeleted: Int = 0,
        val ragStateSynced: Boolean = false,
        val memoriesPruned: Int = 0
    ) {
        val totalFixes: Int
            get() = (if (danglingChatIdCleared) 1 else 0) +
                    (if (danglingPersonaIdCleared) 1 else 0) +
                    (if (danglingModelIdCleared) 1 else 0) +
                    modelsDeactivated +
                    orphanedRagsDeleted +
                    orphanedAvatarsDeleted +
                    (if (ragStateSynced) 1 else 0) +
                    memoriesPruned
    }

    suspend fun runFullCheck(): IntegrityReport = withContext(Dispatchers.IO) {
        Log.i(TAG, "Starting data integrity check...")
        val startTime = System.currentTimeMillis()

        var report = IntegrityReport()

        try {
            report = report.copy(danglingChatIdCleared = checkLastChatId())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking last chat ID", e)
        }

        try {
            report = report.copy(danglingPersonaIdCleared = checkActivePersonaId())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active persona ID", e)
        }

        try {
            report = report.copy(danglingModelIdCleared = checkLastModelId())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking last model ID", e)
        }

        try {
            report = report.copy(modelsDeactivated = checkModelFiles())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking model files", e)
        }

        try {
            report = report.copy(orphanedRagsDeleted = checkRagFiles())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking RAG files", e)
        }

        try {
            report = report.copy(orphanedAvatarsDeleted = checkOrphanedAvatars())
        } catch (e: Exception) {
            Log.e(TAG, "Error checking orphaned avatars", e)
        }

        try {
            ragRepository.syncLoadedStateOnStartup()
            report = report.copy(ragStateSynced = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing RAG state", e)
        }

        try {
            report = report.copy(memoriesPruned = pruneExcessMemories())
        } catch (e: Exception) {
            Log.e(TAG, "Error pruning memories", e)
        }

        val duration = System.currentTimeMillis() - startTime
        Log.i(TAG, "Integrity check complete in ${duration}ms: ${report.totalFixes} fixes applied")
        if (report.totalFixes > 0) {
            Log.i(TAG, "Report: $report")
        }

        report
    }

    /**
     * LAST_CHAT_ID -> deleted chat: clear DataStore ref
     */
    private suspend fun checkLastChatId(): Boolean {
        val lastChatId = appSettings.lastChatId.first() ?: return false

        if (!VaultHelper.isInitialized()) return false

        val chats = VaultHelper.getAllChats()
        val chatExists = chats.any { it.chatId == lastChatId }
        if (!chatExists) {
            Log.w(TAG, "LAST_CHAT_ID '$lastChatId' references deleted chat, clearing")
            appSettings.saveLastChatId(null)
            return true
        }
        return false
    }

    /**
     * ACTIVE_PERSONA_ID -> deleted persona: clear DataStore ref
     */
    private suspend fun checkActivePersonaId(): Boolean {
        val activeId = appSettings.activePersonaId.first() ?: return false

        val persona = personaDao.getById(activeId)
        if (persona == null) {
            Log.w(TAG, "ACTIVE_PERSONA_ID '$activeId' references deleted persona, clearing")
            appSettings.saveActivePersonaId(null)
            return true
        }
        return false
    }

    /**
     * LAST_MODEL_ID -> deleted model: clear DataStore ref
     */
    private suspend fun checkLastModelId(): Boolean {
        val lastModelId = appSettings.lastModelId.first() ?: return false

        val model = modelDao.getById(lastModelId)
        if (model == null) {
            Log.w(TAG, "LAST_MODEL_ID '$lastModelId' references deleted model, clearing")
            appSettings.saveLastModelId(null)
            return true
        }
        return false
    }

    /**
     * Model DB entry -> missing file on disk: deactivate (not delete).
     * User may have moved the file. Only checks file-path models (not content:// URIs).
     */
    private suspend fun checkModelFiles(): Int {
        val models = modelDao.getAllOnce()
        var deactivated = 0

        for (model in models) {
            if (!model.isActive) continue
            if (model.pathType == PathType.CONTENT_URI) continue

            val file = File(model.modelPath)
            if (!file.exists()) {
                Log.w(TAG, "Model '${model.modelName}' file missing at ${model.modelPath}, deactivating")
                modelDao.updateActiveStatus(model.id, false)
                deactivated++
            }
        }

        return deactivated
    }

    /**
     * RAG DB entry -> missing .neuron file: delete DB entry.
     * .neuron files are internal, so missing ones mean orphaned entries.
     */
    private suspend fun checkRagFiles(): Int {
        val rags = ragDao.getAllRagsOnce()
        var deleted = 0

        for (rag in rags) {
            val filePath = rag.filePath ?: continue
            val file = File(filePath)
            if (!file.exists()) {
                Log.w(TAG, "RAG '${rag.name}' file missing at $filePath, deleting DB entry")
                ragDao.deleteById(rag.id)
                deleted++
            }
        }

        return deleted
    }

    /**
     * Avatar file on disk -> no Persona refs it: delete orphaned file.
     */
    private suspend fun checkOrphanedAvatars(): Int {
        val avatarDir = File(context.filesDir, "persona_avatars")
        if (!avatarDir.exists() || !avatarDir.isDirectory) return 0

        val personas = personaDao.getAllOnce()
        val referencedPaths = personas.mapNotNull { it.avatarUri }.toSet()

        var deleted = 0
        avatarDir.listFiles()?.forEach { file ->
            if (file.isFile && file.absolutePath !in referencedPaths) {
                Log.w(TAG, "Orphaned avatar file: ${file.name}, deleting")
                file.delete()
                deleted++
            }
        }

        return deleted
    }

    /**
     * AI memories > 500: prune oldest beyond cap.
     */
    private suspend fun pruneExcessMemories(): Int {
        val count = aiMemoryDao.count()
        if (count <= AI_MEMORY_CAP) return 0

        val allMemories = aiMemoryDao.getAllOnce()
        // Sorted by updatedAt DESC (newest first), so tail is oldest
        val toDelete = allMemories.drop(AI_MEMORY_CAP)

        for (memory in toDelete) {
            aiMemoryDao.delete(memory)
        }

        Log.w(TAG, "Pruned ${toDelete.size} excess AI memories (had $count, cap $AI_MEMORY_CAP)")
        return toDelete.size
    }
}

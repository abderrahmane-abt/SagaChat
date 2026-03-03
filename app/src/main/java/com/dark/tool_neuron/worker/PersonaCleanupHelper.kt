package com.dark.tool_neuron.worker

import android.content.Context
import android.util.Log
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.models.table_schema.Persona
import com.dark.tool_neuron.repo.ums.UmsPersonaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Handles cascade deletion of a persona:
 * 1. Clear ACTIVE_PERSONA_ID from DataStore if this persona is active
 * 2. Delete avatar file from disk
 * 3. Delete DB entry (last, so refs are cleaned first)
 */
object PersonaCleanupHelper {

    private const val TAG = "PersonaCleanupHelper"

    suspend fun deletePersonaWithCascade(
        context: Context,
        persona: Persona,
        personaRepo: UmsPersonaRepository
    ) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Cascade deleting persona: ${persona.name} (${persona.id})")

        // 1. Clear ACTIVE_PERSONA_ID if this persona is active
        try {
            val appSettings = AppSettingsDataStore(context)
            val activeId = appSettings.activePersonaId.first()
            if (activeId == persona.id) {
                Log.d(TAG, "Clearing active persona ID (was ${persona.id})")
                appSettings.saveActivePersonaId(null)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to clear active persona ID", e)
        }

        // 2. Delete avatar file from disk
        persona.avatarUri?.let { uri ->
            try {
                val file = File(uri)
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Deleted avatar file: $uri")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete avatar file: $uri", e)
            }
        }

        // 3. Delete from UMS (last, so refs are cleaned first)
        personaRepo.delete(persona)
        Log.d(TAG, "Persona deleted from UMS: ${persona.name}")
    }
}

package com.dark.tool_neuron.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class AppSettingsDataStore(private val context: Context) {

    companion object {
        private val STREAMING_ENABLED = booleanPreferencesKey("streaming_enabled")
        private val CHAT_MEMORY_ENABLED = booleanPreferencesKey("chat_memory_enabled")
        private val TOOL_CALLING_ENABLED = booleanPreferencesKey("tool_calling_enabled")
        private val IMAGE_BLUR_ENABLED = booleanPreferencesKey("image_blur_enabled")
        private val LOAD_TTS_ON_START = booleanPreferencesKey("load_tts_on_start")
    }

    val streamingEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[STREAMING_ENABLED] ?: true
    }

    val chatMemoryEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[CHAT_MEMORY_ENABLED] ?: true
    }

    val toolCallingEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[TOOL_CALLING_ENABLED] ?: true
    }

    val imageBlurEnabled: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[IMAGE_BLUR_ENABLED] ?: true
    }

    val loadTTSOnStart: Flow<Boolean> = context.appSettingsDataStore.data.map { prefs ->
        prefs[LOAD_TTS_ON_START] ?: true
    }


    suspend fun updateStreamingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[STREAMING_ENABLED] = enabled }
    }

    suspend fun updateChatMemoryEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[CHAT_MEMORY_ENABLED] = enabled }
    }

    suspend fun updateToolCallingEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[TOOL_CALLING_ENABLED] = enabled }
    }

    suspend fun updateImageBlurEnabled(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[IMAGE_BLUR_ENABLED] = enabled }
    }

    suspend fun updateLoadTTSOnStart(enabled: Boolean) {
        context.appSettingsDataStore.edit { it[LOAD_TTS_ON_START] = enabled }
    }

}

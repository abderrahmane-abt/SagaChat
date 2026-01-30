package com.mp.n_apps.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.nappDataStore: DataStore<Preferences> by preferencesDataStore(name = "napp_api")

class NAppDataStore(private val context: Context) {

    companion object {
        private val API_KEY = stringPreferencesKey("napp_api_key")
        private val API_URL = stringPreferencesKey("napp_api_url")
        private val API_MODEL = stringPreferencesKey("napp_api_model")

        const val DEFAULT_URL = "https://api.groq.com/openai"
        const val DEFAULT_MODEL = "openai/gpt-oss-20b"
    }

    val apiKey: Flow<String> = context.nappDataStore.data.map { prefs ->
        prefs[API_KEY] ?: ""
    }

    val apiUrl: Flow<String> = context.nappDataStore.data.map { prefs ->
        prefs[API_URL] ?: DEFAULT_URL
    }

    val apiModel: Flow<String> = context.nappDataStore.data.map { prefs ->
        prefs[API_MODEL] ?: DEFAULT_MODEL
    }

    suspend fun setApiKey(key: String) {
        context.nappDataStore.edit { it[API_KEY] = key }
    }

    suspend fun setApiUrl(url: String) {
        context.nappDataStore.edit { it[API_URL] = url }
    }

    suspend fun setApiModel(model: String) {
        context.nappDataStore.edit { it[API_MODEL] = model }
    }
}

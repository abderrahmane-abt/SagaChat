package com.dark.tool_neuron.repo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.ModelType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.modelRepoDataStore: DataStore<Preferences> by preferencesDataStore(name = "model_repositories")

class ModelRepositoryDataStore(private val context: Context) {

    companion object {
        private val MODEL_REPOS_KEY = stringPreferencesKey("model_repositories")

        val DEFAULT_REPOSITORIES = listOf(
            HFModelRepository(
                id = "qwen2_5_0_5b_instruct",
                name = "Qwen 2.5 Instruct (0.5B)",
                repoPath = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                modelType = ModelType.GGUF
            ),
            HFModelRepository(
                id = "pars_medical_llama_3b",
                name = "Pars Medical LLaMA (3B)",
                repoPath = "HexQuant/Pars-Medical-o1-Llama-FFT-GGUF",
                modelType = ModelType.GGUF
            ),
            HFModelRepository(
                id = "contact_doctor_bio_llama_1b",
                name = "ContactDoctor Bio-Medical LLaMA (1B)",
                repoPath = "DevQuasar/ContactDoctor.Bio-Medical-Llama-3-2-1B-CoT-012025-GGUF",
                modelType = ModelType.GGUF
            ),
            HFModelRepository(
                id = "qwen2_5_coder_0_5b",
                name = "Qwen 2.5 Coder (0.5B)",
                repoPath = "ggml-org/Qwen2.5-Coder-0.5B-Q8_0-GGUF",
                modelType = ModelType.GGUF
            ),
        )
    }

    val repositories: Flow<List<HFModelRepository>> =
        context.modelRepoDataStore.data.map { preferences ->
            val json = preferences[MODEL_REPOS_KEY]
            if (json != null) {
                try {
                    Json.decodeFromString<List<HFModelRepository>>(json)
                } catch (e: Exception) {
                    DEFAULT_REPOSITORIES
                }
            } else {
                DEFAULT_REPOSITORIES
            }
        }

    suspend fun saveRepositories(repos: List<HFModelRepository>) {
        context.modelRepoDataStore.edit { preferences ->
            preferences[MODEL_REPOS_KEY] = Json.encodeToString(repos)
        }
    }

    suspend fun addRepository(repo: HFModelRepository) {
        val current = repositories.first()
        saveRepositories(current + repo)
    }

    suspend fun removeRepository(repoId: String) {
        val current = repositories.first()
        saveRepositories(current.filterNot { it.id == repoId })
    }

    suspend fun toggleRepository(repoId: String) {
        val current = repositories.first()
        saveRepositories(current.map {
            if (it.id == repoId) it.copy(isEnabled = !it.isEnabled)
            else it
        })
    }
}
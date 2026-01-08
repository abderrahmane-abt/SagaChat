package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.state.AppStateManager
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class LLMModelViewModel @Inject constructor(
    private val repository: ModelRepository
) : ViewModel() {

    val installedModels: Flow<List<Model>> = repository.getAllModels()
    val currentModelID = MutableStateFlow("")

    suspend fun getModelConfig(modelId: String): ModelConfig? {
        return repository.getConfigByModelId(modelId)
    }

    fun loadModel(model: Model) {
        viewModelScope.launch {
            try {
                // Update state: Loading
                AppStateManager.setLoadingModel(model.modelName)

                val config = getModelConfig(model.id)
                if (config == null) {
                    AppStateManager.setError("Model configuration not found")
                    return@launch
                }

                // Load the model
                val success = LlmModelWorker.loadGgufModel(model, config)

                if (success) {
                    currentModelID.value = model.id
                    // Update state: Model loaded successfully
                    AppStateManager.setModelLoaded("Model")
                } else {
                    // Update state: Error
                    AppStateManager.setError("Failed to load model")
                }
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Unknown error")
            }
        }
    }

    fun unloadModel() {
        viewModelScope.launch {
            try {
                LlmModelWorker.unloadGgufModel()
                currentModelID.value = ""
                AppStateManager.setModelUnloaded()
            } catch (e: Exception) {
                AppStateManager.setError(e.message ?: "Failed to unload model")
            }
        }
    }
}
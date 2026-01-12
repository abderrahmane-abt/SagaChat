package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.engine_schema.GgufInferenceParams
import com.dark.tool_neuron.models.engine_schema.GgufLoadingParams
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.worker.DiffusionConfig
import dagger.hilt.android.lifecycle.HiltViewModel
import jakarta.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@HiltViewModel
class ModelConfigEditorViewModel @Inject constructor(
    private val repository: ModelRepository
) : ViewModel() {

    val models: Flow<List<Model>> = repository.getAllModels()

    private val _selectedModel = MutableStateFlow<Model?>(null)
    val selectedModel: StateFlow<Model?> = _selectedModel.asStateFlow()

    private val _ggufConfig = MutableStateFlow(GgufEngineSchema())
    val ggufConfig: StateFlow<GgufEngineSchema> = _ggufConfig.asStateFlow()

    private val _diffusionConfig = MutableStateFlow(DiffusionConfig())
    val diffusionConfig: StateFlow<DiffusionConfig> = _diffusionConfig.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _saveSuccess = MutableStateFlow(false)
    val saveSuccess: StateFlow<Boolean> = _saveSuccess.asStateFlow()

    fun selectModel(model: Model) {
        _selectedModel.value = model
        loadConfigForModel(model)
    }

    private fun loadConfigForModel(model: Model) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val config = repository.getConfigByModelId(model.id)

                when (model.providerType) {
                    com.dark.tool_neuron.models.enums.ProviderType.GGUF -> {
                        _ggufConfig.value = if (config != null) {
                            GgufEngineSchema.fromJson(
                                config.modelLoadingParams,
                                config.modelInferenceParams
                            )
                        } else {
                            GgufEngineSchema()
                        }
                    }

                    com.dark.tool_neuron.models.enums.ProviderType.DIFFUSION -> {
                        _diffusionConfig.value = if (config != null) {
                            parseDiffusionConfig(config.modelLoadingParams)
                        } else {
                            DiffusionConfig()
                        }
                    }

                    else -> {}
                }
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun parseDiffusionConfig(jsonString: String?): DiffusionConfig {
        if (jsonString == null) return DiffusionConfig()

        return try {
            val json = org.json.JSONObject(jsonString)
            DiffusionConfig(
                textEmbeddingSize = json.optInt("text_embedding_size", 768),
                runOnCpu = json.optBoolean("run_on_cpu", false),
                useCpuClip = json.optBoolean("use_cpu_clip", true),
                isPony = json.optBoolean("is_pony", false),
                httpPort = json.optInt("http_port", 8081),
                safetyMode = json.optBoolean("safety_mode", false),
                width = json.optInt("width", 512),
                height = json.optInt("height", 512)
            )
        } catch (e: Exception) {
            DiffusionConfig()
        }
    }

    fun saveConfiguration() {
        val model = _selectedModel.value ?: return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val existingConfig = repository.getConfigByModelId(model.id)

                val config = when (model.providerType) {
                    ProviderType.GGUF -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = _ggufConfig.value.toLoadingJson(),
                            modelInferenceParams = _ggufConfig.value.toInferenceJson()
                        )
                    }

                    ProviderType.DIFFUSION -> {
                        ModelConfig(
                            id = existingConfig?.id ?: "",
                            modelId = model.id,
                            modelLoadingParams = _diffusionConfig.value.toJson(),
                            modelInferenceParams = null
                        )
                    }

                    else -> return@launch
                }

                if (existingConfig != null) {
                    repository.updateConfig(config)
                } else {
                    repository.insertConfig(config)
                }

                _saveSuccess.value = true
                delay(2000)
                _saveSuccess.value = false
            } catch (e: Exception) {
                // Handle error
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==================== GGUF Config Updates ====================

    fun updateGgufThreads(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(threads = value))
        }
    }

    fun updateGgufContextSize(value: Int) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(ctxSize = value))
        }
    }

    fun updateGgufUseMmap(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(useMmap = value))
        }
    }

    fun updateGgufUseMlock(value: Boolean) {
        _ggufConfig.update {
            it.copy(loadingParams = it.loadingParams.copy(useMlock = value))
        }
    }

    fun updateGgufTemperature(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(temperature = value))
        }
    }

    fun updateGgufTopK(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(topK = value))
        }
    }

    fun updateGgufTopP(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(topP = value))
        }
    }

    fun updateGgufMinP(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(minP = value))
        }
    }

    fun updateGgufMaxTokens(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(maxTokens = value))
        }
    }

    fun updateGgufMirostat(value: Int) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostat = value))
        }
    }

    fun updateGgufMirostatTau(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostatTau = value))
        }
    }

    fun updateGgufMirostatEta(value: Float) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(mirostatEta = value))
        }
    }

    fun updateGgufSystemPrompt(value: String) {
        _ggufConfig.update {
            it.copy(inferenceParams = it.inferenceParams.copy(systemPrompt = value))
        }
    }

    // ==================== Diffusion Config Updates ====================

    fun updateDiffusionEmbeddingSize(value: Int) {
        _diffusionConfig.update {
            it.copy(textEmbeddingSize = value)
        }
    }

    fun updateDiffusionHttpPort(value: Int) {
        _diffusionConfig.update {
            it.copy(httpPort = value)
        }
    }

    fun updateDiffusionRunOnCpu(value: Boolean) {
        _diffusionConfig.update {
            it.copy(runOnCpu = value)
        }
    }

    fun updateDiffusionUseCpuClip(value: Boolean) {
        _diffusionConfig.update {
            it.copy(useCpuClip = value)
        }
    }

    fun updateDiffusionIsPony(value: Boolean) {
        _diffusionConfig.update {
            it.copy(isPony = value)
        }
    }

    fun updateDiffusionSafetyMode(value: Boolean) {
        _diffusionConfig.update {
            it.copy(safetyMode = value)
        }
    }

    fun updateDiffusionWidth(value: Int) {
        _diffusionConfig.update {
            it.copy(width = value)
        }
    }

    fun updateDiffusionHeight(value: Int) {
        _diffusionConfig.update {
            it.copy(height = value)
        }
    }
}
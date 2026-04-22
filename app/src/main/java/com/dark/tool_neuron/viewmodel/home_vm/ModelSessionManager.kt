package com.dark.tool_neuron.viewmodel.home_vm

import android.content.Context
import androidx.core.net.toUri
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_MAX_TOKENS = 2048

@Singleton
class ModelSessionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
) {
    private val _loadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    val loadState: StateFlow<ModelLoadState> = _loadState.asStateFlow()

    private val _supportsThinking = MutableStateFlow(false)
    val supportsThinking: StateFlow<Boolean> = _supportsThinking.asStateFlow()

    private val _maxTokens = MutableStateFlow(DEFAULT_MAX_TOKENS)
    val maxTokens: StateFlow<Int> = _maxTokens.asStateFlow()

    suspend fun load(model: ModelInfo) {
        _loadState.value = ModelLoadState.Loading(model.id)
        val config = modelRepo.getConfig(model.id)
        _maxTokens.value = readMaxTokens(config)
        val configJson = buildConfigJson(config)
        val result = when (model.pathType) {
            PathType.FILE -> InferenceClient.loadModel(model.path, configJson)
            PathType.CONTENT_URI -> InferenceClient.loadModelFromUri(
                context, model.path.toUri(), configJson,
            )
        }
        result
            .onSuccess {
                modelRepo.setActive(model.id)
                _supportsThinking.value = InferenceClient.supportsThinking()
                _loadState.value = ModelLoadState.Active(model.id)
            }
            .onFailure { err ->
                _supportsThinking.value = false
                _loadState.value = ModelLoadState.Error(model.id, err.message ?: "Load failed")
            }
    }

    suspend fun unload() {
        InferenceClient.unloadModel()
        _supportsThinking.value = false
        _loadState.value = ModelLoadState.Idle
    }

    fun setThinkingEnabled(enabled: Boolean) {
        InferenceClient.setThinkingEnabled(enabled)
    }

    fun stopGeneration() {
        InferenceClient.stopGeneration()
    }

    private fun readMaxTokens(config: ModelConfig?): Int {
        if (config == null) return DEFAULT_MAX_TOKENS
        return try {
            val inf = JSONObject(config.inferenceParamsJson.ifBlank { "{}" })
            inf.optInt("maxTokens", DEFAULT_MAX_TOKENS).coerceIn(64, 32768)
        } catch (_: Exception) {
            DEFAULT_MAX_TOKENS
        }
    }

    private fun buildConfigJson(config: ModelConfig?): String {
        if (config == null) return "{}"
        val sb = StringBuilder(256).append('{')
        val loading = config.loadingParamsJson
        val inference = config.inferenceParamsJson
        if (loading != "{}" && loading.isNotBlank()) {
            val inner = loading.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner).append(',')
        }
        if (inference != "{}" && inference.isNotBlank()) {
            val inner = inference.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner)
        }
        if (sb.last() == ',') sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }
}

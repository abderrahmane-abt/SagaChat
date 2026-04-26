package com.dark.tool_neuron.viewmodel.home_vm

import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.util.VlmPaths
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private const val DEFAULT_MAX_TOKENS = 2048
private const val TAG = "ModelSessionManager"
private const val VLM_PROJECTOR_THREADS = 2

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

    private val _vlmAutoLoadError = MutableStateFlow<String?>(null)
    val vlmAutoLoadError: StateFlow<String?> = _vlmAutoLoadError.asStateFlow()

    fun clearVlmAutoLoadError() { _vlmAutoLoadError.value = null }

    suspend fun load(model: ModelInfo) {
        _loadState.value = ModelLoadState.Loading(model.id)
        _vlmAutoLoadError.value = null
        if (InferenceClient.isVlmLoaded.value) {
            InferenceClient.releaseVlmProjector()
        }
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
                if (model.pathType == PathType.FILE) maybeAutoLoadVlmProjector(model)
            }
            .onFailure { err ->
                _supportsThinking.value = false
                _loadState.value = ModelLoadState.Error(model.id, err.message ?: "Load failed")
            }
    }

    private suspend fun maybeAutoLoadVlmProjector(model: ModelInfo) {
        val baseFile = File(model.path)
        if (!baseFile.exists()) return
        val modelsDir = modelRepo.getModelsDir()
        if (!VlmPaths.isInsideVlmFolder(baseFile.absolutePath, modelsDir)) return
        val preferred = readPreferredProjector(modelRepo.getConfig(model.id))
        val mmproj = VlmPaths.resolveProjector(baseFile, preferred) ?: run {
            _vlmAutoLoadError.value = "Projector file missing from ${baseFile.parentFile?.name}. Download a projector from the repo."
            return
        }
        val ok = withContext(Dispatchers.IO) {
            try {
                InferenceClient.loadVlmProjector(mmproj.absolutePath, VLM_PROJECTOR_THREADS, imageMinTokens = -1, imageMaxTokens = -1)
            } catch (e: Exception) {
                Log.e(TAG, "VLM projector auto-load failed", e)
                _vlmAutoLoadError.value = e.message ?: "Projector load failed"
                false
            }
        }
        if (!ok && _vlmAutoLoadError.value == null) {
            _vlmAutoLoadError.value = "Projector load returned false."
        }
    }

    suspend fun unload() {
        if (InferenceClient.isVlmLoaded.value) {
            InferenceClient.releaseVlmProjector()
        }
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

    private fun readPreferredProjector(config: ModelConfig?): String? {
        if (config == null) return null
        return try {
            val loading = JSONObject(config.loadingParamsJson.ifBlank { "{}" })
            loading.optString("vlmProjector", "").ifBlank { null }
        } catch (_: Exception) { null }
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

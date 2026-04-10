package com.dark.tool_neuron.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.download_manager.HxdManager
import com.dark.download_manager.HxdState
import com.dark.download_manager.HxdStatus
import com.dark.tool_neuron.model.HuggingFaceModel
import com.dark.tool_neuron.model.ModelConfig
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.PathType
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.ModelCatalog
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.service.inference.InferenceClient
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

@HiltViewModel
class ModelStoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val catalog: ModelCatalog,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models

    private val _catalogModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val catalogModels: StateFlow<List<HuggingFaceModel>> = _catalogModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // modelId → hxdDownloadId
    private val _downloadIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadIds: StateFlow<Map<String, Int>> = _downloadIds.asStateFlow()

    // modelId → latest HxdState snapshot
    private val _downloadStates = MutableStateFlow<Map<String, HxdState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, HxdState>> = _downloadStates.asStateFlow()

    val isModelLoaded = InferenceClient.isModelLoaded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    init {
        refreshCatalog()
    }

    fun selectTab(index: Int) { _selectedTab.value = index }

    fun refreshCatalog(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                _catalogModels.value = catalog.getModels(forceRefresh = forceRefresh)
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load models"
            }
            _isLoading.value = false
        }
    }

    fun downloadModel(model: HuggingFaceModel) {
        if (_downloadIds.value.containsKey(model.id)) return
        _downloadStates.value = _downloadStates.value - model.id
        val destFile = modelRepo.modelFile(model.id)
        val hxdId = HxdManager.enqueue(context, model.fileUri, destFile.absolutePath)
        _downloadIds.value = _downloadIds.value + (model.id to hxdId)

        viewModelScope.launch(Dispatchers.IO) {
            HxdManager.observe(hxdId).collect { state ->
                if (state == null) return@collect
                _downloadStates.value = _downloadStates.value + (model.id to state)

                when (state.status) {
                    HxdStatus.COMPLETED -> {
                        modelRepo.insert(
                            ModelInfo(
                                id = model.id,
                                name = model.name,
                                path = destFile.absolutePath,
                                pathType = PathType.FILE,
                                providerType = ProviderType.GGUF,
                                fileSize = if (model.sizeBytes > 0) model.sizeBytes else destFile.length(),
                            )
                        )
                        _downloadIds.value = _downloadIds.value - model.id
                        _downloadStates.value = _downloadStates.value - model.id
                    }
                    HxdStatus.FAILED -> {
                        _downloadIds.value = _downloadIds.value - model.id
                    }
                    HxdStatus.CANCELLED -> {
                        _downloadIds.value = _downloadIds.value - model.id
                        _downloadStates.value = _downloadStates.value - model.id
                    }
                    else -> {}
                }
            }
        }
    }

    fun cancelDownload(modelId: String) {
        _downloadIds.value[modelId]?.let { HxdManager.cancel(it) }
        _downloadIds.value = _downloadIds.value - modelId
        _downloadStates.value = _downloadStates.value - modelId
    }

    fun deleteModel(model: ModelInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            if (model.pathType == PathType.FILE) {
                java.io.File(model.path).delete()
            }
            modelRepo.delete(model.id)
        }
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            val config = modelRepo.getConfig(model.id)
            val configJson = buildConfigJson(config)
            when (model.pathType) {
                PathType.FILE -> InferenceClient.loadModel(model.path, configJson)
                PathType.CONTENT_URI -> {
                    val uri = Uri.parse(model.path)
                    InferenceClient.loadModelFromUri(context, uri, configJson)
                }
            }
            modelRepo.setActive(model.id)
        }
    }

    fun unloadModel() {
        viewModelScope.launch { InferenceClient.unloadModel() }
    }

    fun importLocalModel(uri: Uri, fileName: String, fileSize: Long) {
        modelRepo.insert(
            ModelInfo(
                id = checksumId(uri.toString(), fileName, fileSize),
                name = fileName.removeSuffix(".gguf"),
                path = uri.toString(),
                pathType = PathType.CONTENT_URI,
                providerType = ProviderType.GGUF,
                fileSize = fileSize,
            )
        )
    }

    private fun buildConfigJson(config: ModelConfig?): String {
        if (config == null) return "{}"
        val sb = StringBuffer(256)
        sb.append('{')
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

    private fun checksumId(uri: String, name: String, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(uri.toByteArray())
        digest.update(name.toByteArray())
        digest.update(size.toString().toByteArray())
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }
}

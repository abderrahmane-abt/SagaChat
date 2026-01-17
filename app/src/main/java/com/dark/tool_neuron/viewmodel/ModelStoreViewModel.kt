package com.dark.tool_neuron.viewmodel

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.models.data.HFModelRepository
import com.dark.tool_neuron.models.data.HuggingFaceModel
import com.dark.tool_neuron.models.data.ModelType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.repo.ModelRepositoryDataStore
import com.dark.tool_neuron.repo.ModelStoreRepository
import com.dark.tool_neuron.service.ModelDownloadService
import com.dark.tool_neuron.ui.screen.StoreTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.File

class ModelStoreViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ModelStoreRepository(application)
    private val systemRepo = AppContainer.getModelRepository()
    private val repoDataStore = ModelRepositoryDataStore(application)

    private val _selectedTab = MutableStateFlow(StoreTab.MODELS)
    val selectedTab: StateFlow<StoreTab> = _selectedTab

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _installedModels = MutableStateFlow<List<Model>>(emptyList())
    val installedModels: StateFlow<List<Model>> = _installedModels

    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo

    private val _deleteInProgress = MutableStateFlow<String?>(null)
    val deleteInProgress: StateFlow<String?> = _deleteInProgress

    val repositories = repoDataStore.repositories
    val downloadStates = ModelDownloadService.downloadStates

    // App's internal models directory
    private val appModelsDir = File(application.filesDir, "models")

    init {
        loadDeviceInfo()
        loadModels()
        loadInstalledModels()
    }

    private fun loadDeviceInfo() {
        _deviceInfo.value = repository.getDeviceInfo()
    }

    fun selectTab(tab: StoreTab) {
        _selectedTab.value = tab
    }

    fun loadModels() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                val repos = repositories.first()
                repository.getAvailableModels(repos).onSuccess { modelsList ->
                    _models.value = modelsList
                    _filteredModels.value = modelsList
                }.onFailure { exception ->
                    _error.value = exception.message
                }
            } catch (e: Exception) {
                _error.value = e.message
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadInstalledModels() {
        viewModelScope.launch {
            try {
                val installedList = systemRepo.getAllModels().first()
                _installedModels.value = installedList
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error loading installed models", e)
            }
        }
    }

    fun filterModels(query: String) {
        _filteredModels.value = if (query.isBlank()) {
            _models.value
        } else {
            _models.value.filter {
                it.name.contains(query, ignoreCase = true) || it.description.contains(
                    query,
                    ignoreCase = true
                ) || it.tags.any { tag -> tag.contains(query, ignoreCase = true) }
            }
        }
    }

    fun filterByType(modelType: ModelType?) {
        _filteredModels.value = if (modelType == null) {
            _models.value
        } else {
            _models.value.filter { it.modelType == modelType }
        }
    }

    fun downloadModel(model: HuggingFaceModel) {
        val context = getApplication<Application>()
        val fileUrl = "https://huggingface.co/${model.fileUri}"

        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_START_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, model.id)
            putExtra(ModelDownloadService.EXTRA_MODEL_NAME, model.name)
            putExtra(ModelDownloadService.EXTRA_FILE_URL, fileUrl)
            putExtra(ModelDownloadService.EXTRA_IS_ZIP, model.isZip)
            putExtra(ModelDownloadService.EXTRA_MODEL_TYPE, model.modelType.name)
            putExtra(ModelDownloadService.EXTRA_RUN_ON_CPU, model.runOnCpu)
            putExtra(ModelDownloadService.EXTRA_TEXT_EMBEDDING_SIZE, model.textEmbeddingSize)
        }

        context.startForegroundService(intent)
    }

    fun cancelDownload(modelId: String) {
        val context = getApplication<Application>()
        val intent = Intent(context, ModelDownloadService::class.java).apply {
            action = ModelDownloadService.ACTION_CANCEL_DOWNLOAD
            putExtra(ModelDownloadService.EXTRA_MODEL_ID, modelId)
        }
        context.startService(intent)
    }

    fun deleteModel(model: Model) {
        viewModelScope.launch {
            _deleteInProgress.value = model.id
            try {
                // Delete model file if it's in app's internal directory
                val modelFile = File(model.modelPath)
                if (modelFile.exists() && modelFile.absolutePath.startsWith(appModelsDir.absolutePath)) {
                    val deleted = modelFile.delete()
                    Log.d("ModelStoreViewModel", "Model file deleted: $deleted - ${modelFile.absolutePath}")

                    // If it's a directory (for SD models), delete recursively
                    if (modelFile.isDirectory) {
                        modelFile.deleteRecursively()
                    }
                }

                // Delete config from database
                val config = systemRepo.getConfigByModelId(model.id)
                if (config != null) {
                    systemRepo.deleteConfig(config)
                    Log.d("ModelStoreViewModel", "Model config deleted for: ${model.id}")
                }

                // Delete model from database
                systemRepo.deleteModel(model)
                Log.d("ModelStoreViewModel", "Model deleted from database: ${model.modelName}")

                // Reload installed models
                loadInstalledModels()
            } catch (e: Exception) {
                Log.e("ModelStoreViewModel", "Error deleting model", e)
                _error.value = "Failed to delete model: ${e.message}"
            } finally {
                _deleteInProgress.value = null
            }
        }
    }

    fun addRepository(repo: HFModelRepository) {
        viewModelScope.launch {
            repoDataStore.addRepository(repo)
            loadModels()
        }
    }

    fun removeRepository(repoId: String) {
        viewModelScope.launch {
            repoDataStore.removeRepository(repoId)
            loadModels()
        }
    }

    fun toggleRepository(repoId: String) {
        viewModelScope.launch {
            repoDataStore.toggleRepository(repoId)
            loadModels()
        }
    }
}
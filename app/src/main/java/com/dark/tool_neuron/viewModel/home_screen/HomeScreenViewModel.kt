package com.dark.tool_neuron.viewModel.home_screen

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.model.ModelData
import com.dark.tool_neuron.model.LocalModel
import com.dark.tool_neuron.viewModel.chatViewModel.ChatScreenViewModel
import com.dark.tool_neuron.worker.UIStateManager
import com.mp.ai_engine.models.llm_models.ModelType
import com.mp.ai_engine.workers.installer.ModelInstaller
import com.mp.ai_engine.workers.model.ModelManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/*
    * Load & Un-Load Models ( Text | IMAGE_GEN )
    * */
class HomeScreenViewModel : ViewModel() {

    companion object {
        private const val TAG = "HomeScreenViewModel"
    }

    private val _selectedModel = MutableStateFlow(LocalModel("", "", ModelType.NONE))
    val selectedModel: StateFlow<LocalModel> = _selectedModel.asStateFlow()

    private val _installedModels = MutableStateFlow<List<LocalModel>>(emptyList())
    val installedModels: StateFlow<List<LocalModel>> = _installedModels.asStateFlow()

    private val _isDialogSelected = MutableStateFlow(false)
    val isDialogSelected: StateFlow<Boolean> = _isDialogSelected.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            observeModels()
        }
    }

    suspend fun observeModels() {
        val ggufModel = ModelInstaller.getInstalledGGUFModels()
        val diffusionModels = ModelInstaller.getInstalledDiffusionModels()

        ggufModel.forEach {
            _installedModels.value += LocalModel(it.id, it.modelName, ModelType.TEXT)
        }
        diffusionModels.forEach {
            _installedModels.value += LocalModel(it.id, it.name, ModelType.IMAGE_GEN)
        }
    }


    fun loadModel(modelID: String) {
        if (UIStateManager.isGenerating()) {
            Log.w(TAG, "Cannot change model during generation")
            return
        }

        // Toggle off if same model selected
        if (selectedModel.value.modelId == modelID) {
            Log.d(TAG, "Unselecting model")
            when(_selectedModel.value.modelType){
                ModelType.TEXT -> ModelManager.gguf().unloadModel()
                else -> ModelManager.diffusion().unloadModel()
            }
            _selectedModel.value = LocalModel()
            return
        }
        UIStateManager.toggleStateModelLoading(true)

        viewModelScope.launch(Dispatchers.IO) {

            when(_selectedModel.value.modelType){
                ModelType.TEXT -> ModelManager.gguf().unloadModel()
                else -> ModelManager.diffusion().unloadModel()
            }

            val decodedModel = ModelInstaller.findModel(modelID) ?: return@launch
            decodedModel.ggufModel.let {
                if (it == null) return@let
                val result = ModelManager.gguf().loadTextModel(it.toJson())
                if (result) {
                    _selectedModel.value = LocalModel(it.id, it.modelName, ModelType.TEXT)
                }
            }
            decodedModel.diffusionModel.let {
                if (it == null) return@let
                val result = ModelManager.diffusion().loadModel(it.toJson())
                if (result) {
                    _selectedModel.value = LocalModel(it.id, it.name, ModelType.IMAGE_GEN)
                }
            }
            UIStateManager.toggleStateModelLoading(false)
        }
    }

    fun setIsDialogOpen(boolean: Boolean){
        _isDialogSelected.value = boolean
    }
}
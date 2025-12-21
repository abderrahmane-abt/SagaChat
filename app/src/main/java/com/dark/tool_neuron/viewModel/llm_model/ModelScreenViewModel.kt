package com.dark.tool_neuron.viewModel.llm_model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mp.ai_engine.models.image_models.DiffusionDatabaseModel
import com.mp.ai_engine.models.image_models.toCloudModel
import com.mp.ai_engine.models.llm_models.CloudModel
import com.mp.ai_engine.models.llm_models.GGUFDatabaseModel
import com.mp.ai_engine.models.llm_models.toCloudModel
import com.mp.ai_engine.workers.installer.ModelInstaller
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ModelScreenViewModel : ViewModel() {
    private val _ggufModels = MutableStateFlow<List<GGUFDatabaseModel>>(emptyList())
    val ggufModels = _ggufModels.asStateFlow()

    private val _diffusionModels = MutableStateFlow<List<DiffusionDatabaseModel>>(emptyList())
    val diffusionModels = _diffusionModels.asStateFlow()

    private val _cloudModels = MutableStateFlow<List<CloudModel>>(emptyList())
    val cloudModels = _cloudModels.asStateFlow()

    init {
        observeModels()
    }

    fun observeModels() {
        viewModelScope.launch {
            _ggufModels.value = ModelInstaller.getInstalledGGUFModels()
            _diffusionModels.value = ModelInstaller.getInstalledDiffusionModels()

            _ggufModels.value.forEach {
                _cloudModels.value += it.toCloudModel()
            }
            _diffusionModels.value.forEach {
                _cloudModels.value += it.toCloudModel()
            }
        }
    }

}
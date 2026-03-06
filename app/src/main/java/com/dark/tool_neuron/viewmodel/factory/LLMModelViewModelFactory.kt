package com.dark.tool_neuron.viewmodel.factory

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.dark.tool_neuron.viewmodel.LLMModelViewModel

class LLMModelViewModelFactory(
    private val application: Application
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LLMModelViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LLMModelViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

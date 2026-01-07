package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.engine.GenerationEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ChatViewModel(private val engine: GGUFEngine) : ViewModel() {

    private val _messages = MutableStateFlow<List<String>>(emptyList())
    val messages: StateFlow<List<String>> = _messages

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    fun generate(prompt: String, maxTokens: Int = 512) {
        viewModelScope.launch {
            _isGenerating.value = true
            val currentText = StringBuilder()

            engine.generateFlow(prompt, maxTokens).collect { event ->
                when (event) {
                    is GenerationEvent.Token -> {
                        currentText.append(event.text)
                        _messages.value += currentText.toString()
                    }
                    is GenerationEvent.Done -> {
                        _isGenerating.value = false
                    }
                    is GenerationEvent.Error -> {
                        _isGenerating.value = false
                    }
                    is GenerationEvent.ToolCall -> {}
                }
            }
        }
    }

    fun stop() {
        engine.stopGeneration()
        _isGenerating.value = false
    }
}
package com.dark.neuroverse.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
import com.mp.ai_core.NativeLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class TempViewModel : ViewModel() {
    //Define State Variables
    private var _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages

    private var nativeLib = NativeLib()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            //Initialize NativeLib
            val model = ModelManager.getFirstModel()
            if (model == null) return@launch
            nativeLib.initModel(model.modelPath)
        }
    }

    //Public Methods
    fun sendMessage(input: String) {
        //Add user message to the list
        _messages.value += Message(role = Role.User, text = input)
        var token = ""

        //Get response from NativeLib
        nativeLib.generateStreaming(
            prompt = input.trim(),
            uiScope = viewModelScope,
            onStart = {
                _messages.value += Message(id = "-1", role = Role.Assistant, text = token)
            },
            onGenerate = { tk ->
                token += tk
                _messages.update {
                    it.map { message ->
                        if (message.id == "-1") {
                            message.copy(text = token)
                        } else {
                            message
                        }
                    }
                }
            },
            onDone = {
                _messages.update {
                    it.map { message ->
                        if (message.id == "-1") {
                            message.copy(id = UUID.randomUUID().toString())
                        } else {
                            message
                        }
                    }
                }
            },
            onError = {
                _messages.value += Message(role = Role.Error, text = it)
            })
    }
}


package com.dark.neuroverse.viewModel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.data.ModelsList
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.Role
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

    init {
        viewModelScope.launch(Dispatchers.IO) {
            //Initialize NativeLib
            val model = ModelManager.getFirstModel()
            if (model == null) return@launch

            Log.d("Model", "Model: ${model.modelPath}")
            val systemPrompt = """
                         You are a precise, concise assistant.

                         ## Protocol
                         - Turns are delimited by tokens `<|im_start|>role … <|im_end|>`.
                         - When you **need a tool**, respond with **JSON only**:
                           {
                             "type": "tool_call",
                             "tool": "<tool_name>",
                             "arguments": { ... }   // strictly JSON, no comments, no trailing commas
                           }
                         - When you **do not** need a tool, respond with **JSON only**:
                           {
                             "type": "final",
                             "content": "<your answer here>"
                           }
                         - Never emit any extra prose, markdown, or explanations outside those JSON envelopes.

                         ## Tool results
                         - Tool outputs arrive as a message with role = `tool`, content = raw tool result (usually JSON).
                         - You may use tool results in your reasoning, but your next output must still follow the JSON envelope above.

                         ## Quality & Truthfulness
                         - If info is missing/uncertain: state the limitation briefly, then proceed with the best safe answer.
                         - No fabrications about sources, links, or capabilities.
                         - Keep answers short and on-topic by default.

                         ## Safety
                         - Refuse unsafe requests with a brief reason and a safer alternative where relevant.
                         - No disallowed content.

                         ## Style
                         - Plain language. Minimal fluff. Use lists sparingly.
                         - Numbers, code, and JSON must be syntactically valid.

                         ## Checklist before sending
                         - Output is a single JSON object.
                         - If using a tool: correct "tool" name and well-formed "arguments".
                         - If final: put textual reply in "content".
                         - No trailing commas, no comments, no markdown fences.
 
                        """.trimIndent()

            ModelManager.loadModel(
                modelData = model,
               // defaults = ModelManager.ManagerDefaults(systemPrompt = systemPrompt),
                chatTemplate = ModelsList.chatTemplate,
                forceReload = true
            ) {
                Log.d("Model", "Model loaded successfully $model")
            }
        }
    }

    //Public Methods
    fun sendMessage(input: String) {
        //Add user message to the list
        _messages.value += Message(role = Role.User, text = input)
        var token = ""

        viewModelScope.launch(Dispatchers.IO) {
            _messages.value += Message(role = Role.Assistant, text = "", id = "-1")

            val response = Neuron.generateStreaming(
                prompt = input, onToken = { tok ->
                    token += tok
                    _messages.update {
                        it.map { message ->
                            if (message.id == "-1") {
                                message.copy(text = token)
                            } else {
                                message
                            }
                        }
                    }
                })

            response.let {
                _messages.update {
                    it.map { message ->
                        if (message.id == "-1") {
                            message.copy(id = UUID.randomUUID().toString())
                        } else {
                            message
                        }
                    }
                }
            }
        }
    }

    fun stopGenerating() {
        Neuron.stopGeneration()
    }
}


package com.dark.neuroverse.viewModel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject

class NeuroVScreenViewModel: ViewModel() {
    private val _result = MutableStateFlow(JSONObject())
    val result: StateFlow<JSONObject> = _result

    fun updateResult(result: JSONObject) {
        _result.value = result
    }
}
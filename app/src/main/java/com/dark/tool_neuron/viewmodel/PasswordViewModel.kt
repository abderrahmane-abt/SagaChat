package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PasswordViewModel @Inject constructor(
    private val securityManager: SecurityManager
) : ViewModel() {

    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _isVerifying = MutableStateFlow(false)
    val isVerifying = _isVerifying.asStateFlow()

    private val _unlocked = MutableStateFlow(false)
    val unlocked = _unlocked.asStateFlow()

    val maxLength = 16

    fun appendDigit(digit: Char) {
        if (_password.value.length < maxLength) {
            _password.value += digit
            _error.value = null
        }
    }

    fun deleteLast() {
        val current = _password.value
        if (current.isNotEmpty()) {
            _password.value = current.dropLast(1)
            _error.value = null
        }
    }

    fun clearAll() {
        _password.value = ""
        _error.value = null
    }

    fun reset() {
        _password.value = ""
        _error.value = null
        _isVerifying.value = false
        _unlocked.value = false
    }

    fun submit() {
        val pwd = _password.value
        if (pwd.length < 4) {
            _error.value = "At least 4 characters"
            return
        }

        _isVerifying.value = true
        viewModelScope.launch(Dispatchers.Default) {
            val valid = securityManager.verifyPassword(pwd)
            _isVerifying.value = false
            if (valid) {
                _unlocked.value = true
            } else {
                _password.value = ""
                _error.value = "Wrong password"
            }
        }
    }
}

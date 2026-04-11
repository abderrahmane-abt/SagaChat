package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.data.SecurityManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager
) : ViewModel() {

    private val _selectedMode = MutableStateFlow<String?>(null)
    val selectedMode = _selectedMode.asStateFlow()

    // Password setup flow
    private val _password = MutableStateFlow("")
    val password = _password.asStateFlow()

    private val _confirmPassword = MutableStateFlow("")
    val confirmPassword = _confirmPassword.asStateFlow()

    private val _isConfirmStep = MutableStateFlow(false)
    val isConfirmStep = _isConfirmStep.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun selectMode(mode: String) {
        _selectedMode.value = mode
        _password.value = ""
        _confirmPassword.value = ""
        _isConfirmStep.value = false
        _error.value = null
    }

    fun appendDigit(digit: Char) {
        if (_isConfirmStep.value) {
            if (_confirmPassword.value.length < 16) {
                _confirmPassword.value += digit
                _error.value = null
            }
        } else {
            if (_password.value.length < 16) {
                _password.value += digit
                _error.value = null
            }
        }
    }

    fun deleteLast() {
        if (_isConfirmStep.value) {
            val c = _confirmPassword.value
            if (c.isNotEmpty()) _confirmPassword.value = c.dropLast(1)
        } else {
            val p = _password.value
            if (p.isNotEmpty()) _password.value = p.dropLast(1)
        }
        _error.value = null
    }

    fun clearAll() {
        if (_isConfirmStep.value) {
            _confirmPassword.value = ""
        } else {
            _password.value = ""
        }
        _error.value = null
    }

    fun submitPassword(): Boolean {
        val pwd = _password.value
        if (pwd.length < 4) {
            _error.value = "At least 4 characters"
            return false
        }

        if (!_isConfirmStep.value) {
            _isConfirmStep.value = true
            return false
        }

        if (_confirmPassword.value != pwd) {
            _confirmPassword.value = ""
            _error.value = "Passwords don't match"
            return false
        }

        security.setPassword(pwd)
        prefs.setupDone = true
        prefs.securitySetupDone = true
        prefs.onboardingComplete = true
        return true
    }

    fun goBack() {
        if (_isConfirmStep.value) {
            _isConfirmStep.value = false
            _confirmPassword.value = ""
            _error.value = null
        } else {
            _selectedMode.value = null
            _password.value = ""
        }
    }

    fun completeWithNoLock() {
        security.disableLock()
        prefs.setupDone = true
        prefs.securitySetupDone = true
        prefs.onboardingComplete = true
    }
}

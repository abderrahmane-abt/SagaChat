package com.dark.tool_neuron.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppSettingsDataStore
import com.dark.tool_neuron.tts.TTSDataStore
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.tts.TTSSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val appSettingsDataStore = AppSettingsDataStore(application)
    private val ttsDataStore = TTSDataStore(application)

    // App settings
    val streamingEnabled: StateFlow<Boolean> = appSettingsDataStore.streamingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val chatMemoryEnabled: StateFlow<Boolean> = appSettingsDataStore.chatMemoryEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val toolCallingEnabled: StateFlow<Boolean> = appSettingsDataStore.toolCallingEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val imageBlurEnabled: StateFlow<Boolean> = appSettingsDataStore.imageBlurEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val loadTTSOnStart: StateFlow<Boolean> = appSettingsDataStore.loadTTSOnStart
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // TTS settings
    val ttsSettings: StateFlow<TTSSettings> = ttsDataStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TTSSettings())

    val ttsModelLoaded: StateFlow<Boolean> = TTSManager.isModelLoaded
    val ttsAvailableVoices: StateFlow<List<String>> = TTSManager.availableVoices

    // App info
    val appVersion: String = try {
        val pInfo = application.packageManager.getPackageInfo(application.packageName, 0)
        pInfo.versionName ?: "1.0"
    } catch (_: Exception) {
        "1.0"
    }

    // App settings updaters
    fun setStreamingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateStreamingEnabled(enabled) }
    }

    fun setChatMemoryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateChatMemoryEnabled(enabled) }
    }

    fun setToolCallingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateToolCallingEnabled(enabled) }
    }

    fun setImageBlurEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateImageBlurEnabled(enabled) }
    }

    fun setLoadTTSOnStart(enabled: Boolean) {
        viewModelScope.launch { appSettingsDataStore.updateLoadTTSOnStart(enabled) }
    }

    // TTS settings updaters
    fun updateVoice(voice: String) {
        viewModelScope.launch { ttsDataStore.updateVoice(voice) }
    }

    fun updateSpeed(speed: Float) {
        viewModelScope.launch { ttsDataStore.updateSpeed(speed) }
    }

    fun updateSteps(steps: Int) {
        viewModelScope.launch { ttsDataStore.updateSteps(steps) }
    }

    fun updateLanguage(language: String) {
        viewModelScope.launch { ttsDataStore.updateLanguage(language) }
    }

    fun updateAutoSpeak(enabled: Boolean) {
        viewModelScope.launch { ttsDataStore.updateAutoSpeak(enabled) }
    }

    fun updateUseNNAPI(enabled: Boolean) {
        viewModelScope.launch { ttsDataStore.updateUseNNAPI(enabled) }
    }
}

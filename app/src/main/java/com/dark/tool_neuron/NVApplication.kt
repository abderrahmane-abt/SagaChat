package com.dark.tool_neuron

import android.app.Application
import android.util.Log
import com.dark.tool_neuron.di.AppContainer
import com.dark.tool_neuron.plugins.CalculatorPlugin
import com.dark.tool_neuron.plugins.DevUtilsPlugin
import com.dark.tool_neuron.plugins.PluginManager
import com.dark.tool_neuron.plugins.WebSearchPlugin
import com.dark.tool_neuron.tts.TTSManager
import com.dark.tool_neuron.worker.LlmModelWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class NVApplication : Application() {

    companion object {
        private const val TAG = "NVApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")

        // Initialize app container first
        AppContainer.init(applicationContext, this)

        // Register plugins
        PluginManager.registerPlugin(WebSearchPlugin())
        PluginManager.registerPlugin(CalculatorPlugin())
        PluginManager.registerPlugin(DevUtilsPlugin())
        Log.d(TAG, "Plugins registered: WebSearch, Calculator, DevUtils")

        // Initialize TTS Manager (auto-loads model if previously downloaded)
        TTSManager.init(applicationContext)
        Log.d(TAG, "TTSManager initialized")

        // Note: Service binding moved to MainActivity to comply with Android 14+ foreground service restrictions
    }

    override fun onTerminate() {
        super.onTerminate()
        Log.d(TAG, "Application onTerminate")
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Don't unbind on configuration changes
        if (level == TRIM_MEMORY_UI_HIDDEN) {
            Log.d(TAG, "UI hidden, keeping service bound")
        }
    }
}
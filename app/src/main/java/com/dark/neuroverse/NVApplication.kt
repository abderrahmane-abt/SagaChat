package com.dark.neuroverse

import android.app.Application
import com.dark.ai_module.model.ModelParams
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.data.UserPrefs
import com.dark.plugins.manager.PluginManager
import com.mp.data_hub_lib.manager.DataHubManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/**
 * NVApplication — trims startup work, avoids blocking main thread,
 * and preloads a preferred or first-available model if present.
 */
class NVApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Synchronous, cheap initializations
        ModelManager.init(applicationContext)
        PluginManager.init(applicationContext)
        DataHubManager.init(applicationContext)
        // Lightweight background bootstrap
        appScope.launch {
            // 1) Apply user-tuned decoding params (with safe fallbacks)
            val professional = UserPrefs.getModelPParams(applicationContext).firstOrNull() ?: 2.5f
            val emotional = UserPrefs.getModelEParams(applicationContext).firstOrNull() ?: 7.3f
            ModelManager.updateModelParams(
                ModelParams.Professional(professional),
                ModelParams.Emotional(emotional)
            )
        }
    }

    companion object {
        private const val TAG = "NVApplication"
    }
}

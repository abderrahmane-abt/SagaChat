package com.dark.neuroverse

import android.app.Application
import android.util.Log
import com.dark.ai_manager.ai.local.Neuron
import com.dark.ai_manager.ai.local.NeuronVariant
import com.dark.neuroverse.utils.CHATTING_SYSTEM_PROMPT
import com.dark.neuroverse.utils.taskRouterSystemPrompt
import com.dark.neuroverse.worker.model.ModelManager
import com.dark.task_manager.register.TaskRegistry

class NeuroVerseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuroVerseApplication", "✅ Application started")
        TaskRegistry.init(this)
        ModelManager.init(this)
        //Neuron.loadModel(variant = NeuronVariant.NVChat, systemPrompt = CHATTING_SYSTEM_PROMPT)
        Neuron.loadModel(NeuronVariant.NVAdvanceRouter, systemPrompt = com.dark.task_manager.data.taskRouterSystemPrompt)
    }
}

package com.dark.neuroverse

import android.app.Application
import android.util.Log
import com.dark.task_manager.register.TaskRegistry

class NeuroVerseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("NeuroVerseApplication", "✅ Application started")
        TaskRegistry.init()
    }
}

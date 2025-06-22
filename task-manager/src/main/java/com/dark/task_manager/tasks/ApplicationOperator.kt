package com.dark.task_manager.tasks

import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo

class ApplicationOperator: TaskApi {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Application Operator",
            description = ""
        )
    }

    override fun onStart() {
        Log.d(getTaskInfo().taskName, "ApplicationTask started")
    }

    override fun onRun() {
        Log.d(getTaskInfo().taskName, "ApplicationTask running")
    }

    override fun onStop() {
        Log.d(getTaskInfo().taskName, "ApplicationTask stopped")
    }
}
package com.dark.task_manager.api

import com.dark.task_manager.model.TaskInfo

interface TaskApi {
    fun getTaskInfo(): TaskInfo
    fun onStart()
    fun onRun()
    fun onStop()
}
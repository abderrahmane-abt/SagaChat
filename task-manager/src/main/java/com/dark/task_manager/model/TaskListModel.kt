package com.dark.task_manager.model

import com.dark.task_manager.api.TaskApi

data class TaskListModel(val taskInfo: TaskInfo, val taskApi: TaskApi, val taskType: TaskType)
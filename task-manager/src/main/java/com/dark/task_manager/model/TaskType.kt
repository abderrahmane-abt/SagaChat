package com.dark.task_manager.model

enum class TaskType(val id: Int) {
    FOREGROUND(0),
    BACKGROUND(1),
    NONE(-1),
}
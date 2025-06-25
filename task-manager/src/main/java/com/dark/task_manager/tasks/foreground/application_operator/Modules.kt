package com.dark.task_manager.tasks.foreground.application_operator

import android.graphics.drawable.Drawable

data class AppInfo(
    val appName: String,
    val packageName: String,
    val icon: Drawable
)
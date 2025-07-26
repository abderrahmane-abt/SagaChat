package com.dark.neuroverse.ui.components

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun ErrorBox(error: Throwable?) {
    val msg = buildString {
        appendLine(error?.toString() ?: "Unknown error")
        appendLine()
        appendLine(error?.stackTraceToString() ?: "")
    }
    Text(
        text = msg,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
    )
    Log.e("PluginHost", "Plugin load failed\n$msg")
}

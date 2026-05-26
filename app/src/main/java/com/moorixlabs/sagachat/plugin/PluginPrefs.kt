package com.moorixlabs.sagachat.plugin

data class PluginPrefs(
    val pluginId: String,
    val enabled: Boolean,
    val configJson: String = "{}",
)

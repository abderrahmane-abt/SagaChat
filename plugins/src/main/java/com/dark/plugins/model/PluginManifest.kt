package com.dark.plugins.model

data class PluginManifest(
    val name: String = "",
    val description: String = "",
    val mainClass: String = "",
    val tools: List<Tools> = emptyList(),
    val version: String = "",
    val rawCode: String = "",
    val rawToolsCode: String = ""
)

data class Tools(
    val toolName: String = "",
    val path: String = "",
    val args: Map<String, Any?> = emptyMap() // allow numbers/bools too

)
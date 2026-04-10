package com.dark.tool_neuron.model

data class HuggingFaceModel(
    val id: String,
    val name: String,
    val fileName: String,
    val fileUri: String,
    val approximateSize: String,
    val sizeBytes: Long = 0,
    val repoId: String = "",
    val quantization: String = "",
    val tags: List<String> = emptyList(),
)

data class HFRepository(
    val id: String,
    val name: String,
    val repoPath: String,
    val isEnabled: Boolean = true,
    val category: String = "general",
)

package com.dark.tool_neuron.models.data

enum class ModelCategory {
    GENERAL,
    MEDICAL,
    RESEARCH,
    CODING,
    BUSINESS,
    CYBERSECURITY;

    val displayName: String
        get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

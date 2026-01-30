package com.mp.n_apps.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class NAppStateSchema(
    val schema: Map<String, StateField> = emptyMap(),
    val persistence: String = "none",
    val computed: Map<String, String> = emptyMap()
)

@Serializable
data class StateField(
    val type: String,
    val default: JsonElement = JsonNull,
    val items: StateField? = null,
    val properties: Map<String, StateField>? = null
) {
    companion object {
        val VALID_TYPES = setOf("number", "string", "boolean", "array", "object")
    }
}

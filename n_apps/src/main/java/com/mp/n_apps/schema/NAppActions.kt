package com.mp.n_apps.schema

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class NAppActionsSchema(
    val actions: Map<String, NAppAction> = emptyMap()
)

@Serializable
data class NAppAction(
    val type: String,

    // State actions: set_state, toggle_state, increment, decrement
    val target: String? = null,
    val value: JsonElement? = null,
    val amount: JsonElement? = null,

    // Control: conditional
    val condition: String? = null,
    @SerialName("then") val thenAction: String? = null,
    @SerialName("else") val elseAction: String? = null,

    // Control: batch, sequence
    val actions: List<String>? = null,

    // Array: array_push
    val item: JsonElement? = null,

    // Array: array_remove, array_set
    val index: JsonElement? = null,

    // System: toast
    val message: String? = null,
    val duration: String? = null,

    // AI: ai_call
    val prompt: String? = null,
    val resultTarget: String? = null,
    val loadingTarget: String? = null
) {
    companion object {
        val VALID_TYPES = setOf(
            // State
            "set_state", "toggle_state", "increment", "decrement",
            // Control
            "batch", "sequence", "conditional",
            // Array
            "array_push", "array_remove", "array_clear", "array_set",
            // System
            "toast",
            // AI
            "ai_call"
        )
    }
}

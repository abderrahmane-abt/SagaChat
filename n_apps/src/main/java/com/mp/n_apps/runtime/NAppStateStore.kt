package com.mp.n_apps.runtime

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.mp.n_apps.expression.Evaluator
import com.mp.n_apps.schema.NAppStateSchema
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull

class NAppStateStore {

    private val _state: SnapshotStateMap<String, Any?> = mutableStateMapOf()
    private var computedExpressions: Map<String, String> = emptyMap()

    val state: Map<String, Any?> get() = _state

    fun initializeFrom(schema: NAppStateSchema) {
        _state.clear()
        schema.schema.forEach { (key, field) ->
            _state[key] = jsonToKotlin(field.default, field.type)
        }
        computedExpressions = schema.computed
    }

    operator fun get(key: String): Any? {
        // Check computed first
        computedExpressions[key]?.let { expr ->
            return try {
                Evaluator.parseAndEvaluate(expr, _state)
            } catch (_: Exception) {
                null
            }
        }
        return _state[key]
    }

    operator fun set(key: String, value: Any?) {
        _state[key] = value
    }

    fun getSnapshot(): Map<String, Any?> {
        val snapshot = _state.toMap().toMutableMap()
        computedExpressions.forEach { (key, expr) ->
            try {
                snapshot[key] = Evaluator.parseAndEvaluate(expr, _state)
            } catch (_: Exception) {
                // Keep existing value or null
            }
        }
        return snapshot
    }

    fun getAsString(key: String): String = Evaluator.toStr(get(key))

    fun getAsBoolean(key: String): Boolean = Evaluator.toBool(get(key))

    fun getAsNumber(key: String): Double = Evaluator.toNum(get(key))

    @Suppress("UNCHECKED_CAST")
    fun getAsList(key: String): MutableList<Any?> {
        val value = get(key)
        return when (value) {
            is MutableList<*> -> value as MutableList<Any?>
            is List<*> -> value.toMutableList()
            else -> mutableListOf()
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getAsMap(key: String): MutableMap<String, Any?> {
        val value = get(key)
        return when (value) {
            is MutableMap<*, *> -> value as MutableMap<String, Any?>
            is Map<*, *> -> value.entries.associate { it.key.toString() to it.value }.toMutableMap()
            else -> mutableMapOf()
        }
    }

    companion object {
        fun jsonToKotlin(element: JsonElement, typeHint: String? = null): Any? = when (element) {
            is JsonNull -> when (typeHint) {
                "number" -> 0.0
                "string" -> ""
                "boolean" -> false
                "array" -> mutableListOf<Any?>()
                "object" -> mutableMapOf<String, Any?>()
                else -> null
            }
            is JsonPrimitive -> when {
                element.booleanOrNull != null -> element.boolean
                element.doubleOrNull != null -> element.double
                element.isString -> element.content
                else -> element.content
            }
            is JsonArray -> element.map { jsonToKotlin(it) }.toMutableList()
            is JsonObject -> element.entries.associate { (k, v) ->
                k to jsonToKotlin(v)
            }.toMutableMap()
        }
    }
}

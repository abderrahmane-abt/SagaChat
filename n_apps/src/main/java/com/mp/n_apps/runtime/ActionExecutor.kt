package com.mp.n_apps.runtime

import com.mp.n_apps.expression.Evaluator
import com.mp.n_apps.schema.NAppAction
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull

class ActionExecutor(
    private val stateStore: NAppStateStore,
    private val resolver: ExpressionResolver,
    private val actions: Map<String, NAppAction>,
    var onToast: ((message: String, duration: String) -> Unit)? = null,
    var onAICall: ((prompt: String, resultTarget: String?, loadingTarget: String?) -> Unit)? = null
) {

    fun execute(actionId: String) {
        val action = actions[actionId] ?: return
        executeAction(action)
    }

    private fun executeAction(action: NAppAction) {
        val state = stateStore.getSnapshot()

        when (action.type) {
            "set_state" -> {
                val target = action.target ?: return
                val value = resolveActionValue(action.value, state)
                stateStore[target] = value
            }

            "toggle_state" -> {
                val target = action.target ?: return
                stateStore[target] = !stateStore.getAsBoolean(target)
            }

            "increment" -> {
                val target = action.target ?: return
                val amount = resolveNumberValue(action.amount, state) ?: 1.0
                stateStore[target] = stateStore.getAsNumber(target) + amount
            }

            "decrement" -> {
                val target = action.target ?: return
                val amount = resolveNumberValue(action.amount, state) ?: 1.0
                stateStore[target] = stateStore.getAsNumber(target) - amount
            }

            "batch" -> {
                action.actions?.forEach { subId ->
                    execute(subId)
                }
            }

            "sequence" -> {
                action.actions?.forEach { subId ->
                    execute(subId)
                }
            }

            "conditional" -> {
                val condition = action.condition ?: return
                val result = resolver.resolveBoolean(condition, stateStore.getSnapshot())
                if (result) {
                    action.thenAction?.let { execute(it) }
                } else {
                    action.elseAction?.let { execute(it) }
                }
            }

            "array_push" -> {
                val target = action.target ?: return
                val item = resolveActionValue(action.item, state)
                val list = stateStore.getAsList(target)
                list.add(item)
                stateStore[target] = list
            }

            "array_remove" -> {
                val target = action.target ?: return
                val index = resolveNumberValue(action.index, state)?.toInt() ?: return
                val list = stateStore.getAsList(target)
                if (index in list.indices) {
                    list.removeAt(index)
                    stateStore[target] = list
                }
            }

            "array_clear" -> {
                val target = action.target ?: return
                stateStore[target] = mutableListOf<Any?>()
            }

            "array_set" -> {
                val target = action.target ?: return
                val index = resolveNumberValue(action.index, state)?.toInt() ?: return
                val value = resolveActionValue(action.value, state)
                val list = stateStore.getAsList(target)
                if (index in list.indices) {
                    list[index] = value
                    stateStore[target] = list
                }
            }

            "toast" -> {
                val message = action.message?.let { resolver.resolveString(it, stateStore.getSnapshot()) } ?: return
                val duration = action.duration ?: "short"
                onToast?.invoke(message, duration)
            }

            "ai_call" -> {
                val prompt = action.prompt?.let { resolver.resolveString(it, stateStore.getSnapshot()) } ?: return
                action.loadingTarget?.let { stateStore[it] = true }
                onAICall?.invoke(prompt, action.resultTarget, action.loadingTarget)
            }
        }
    }

    private fun resolveActionValue(element: JsonElement?, state: Map<String, Any?>): Any? {
        if (element == null || element is JsonNull) return null
        if (element is JsonPrimitive) {
            if (element.isString) {
                val content = element.content
                // Check if it's an expression
                if (content.contains("{{")) {
                    return resolver.resolveAny(content, state)
                }
                // Check for typed primitives
                element.doubleOrNull?.let { return it }
                if (content == "true") return true
                if (content == "false") return false
                return content
            }
            element.doubleOrNull?.let { return it }
            return element.content
        }
        return NAppStateStore.jsonToKotlin(element)
    }

    private fun resolveNumberValue(element: JsonElement?, state: Map<String, Any?>): Double? {
        if (element == null || element is JsonNull) return null
        if (element is JsonPrimitive) {
            element.doubleOrNull?.let { return it }
            if (element.isString && element.content.contains("{{")) {
                val result = resolver.resolveAny(element.content, state)
                return Evaluator.toNum(result)
            }
            return element.content.toDoubleOrNull()
        }
        return null
    }
}

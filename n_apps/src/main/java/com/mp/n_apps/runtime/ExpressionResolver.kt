package com.mp.n_apps.runtime

import com.mp.n_apps.expression.Evaluator

class ExpressionResolver {

    private val templateRegex = Regex("""\{\{(.+?)\}\}""")

    fun resolveString(template: String, state: Map<String, Any?>): String {
        // If the entire string is a single expression, return its string form
        val singleMatch = templateRegex.matchEntire(template.trim())
        if (singleMatch != null) {
            val expr = singleMatch.groupValues[1].trim()
            return try {
                val result = Evaluator.parseAndEvaluate(expr, state)
                Evaluator.toStr(result)
            } catch (_: Exception) {
                template
            }
        }

        // Otherwise, interpolate all {{...}} blocks
        return templateRegex.replace(template) { match ->
            val expr = match.groupValues[1].trim()
            try {
                val result = Evaluator.parseAndEvaluate(expr, state)
                Evaluator.toStr(result)
            } catch (_: Exception) {
                match.value
            }
        }
    }

    fun resolveBoolean(expression: String, state: Map<String, Any?>): Boolean {
        // Strip surrounding {{ }} if present
        val cleaned = expression.trim().let {
            if (it.startsWith("{{") && it.endsWith("}}")) {
                it.substring(2, it.length - 2).trim()
            } else {
                it
            }
        }
        return try {
            val result = Evaluator.parseAndEvaluate(cleaned, state)
            Evaluator.toBool(result)
        } catch (_: Exception) {
            true // default visible, default enabled
        }
    }

    fun resolveAny(expression: String, state: Map<String, Any?>): Any? {
        val cleaned = expression.trim().let {
            if (it.startsWith("{{") && it.endsWith("}}")) {
                it.substring(2, it.length - 2).trim()
            } else {
                it
            }
        }
        return try {
            Evaluator.parseAndEvaluate(cleaned, state)
        } catch (_: Exception) {
            null
        }
    }
}

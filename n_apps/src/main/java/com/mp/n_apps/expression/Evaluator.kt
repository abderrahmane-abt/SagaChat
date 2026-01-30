package com.mp.n_apps.expression

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong

class Evaluator(private val state: Map<String, Any?>) {

    fun evaluate(node: ExprNode): Any? = when (node) {
        is ExprNode.NumberLiteral -> node.value
        is ExprNode.StringLiteral -> node.value
        is ExprNode.BooleanLiteral -> node.value
        is ExprNode.NullLiteral -> null
        is ExprNode.ArrayLiteral -> node.elements.map { evaluate(it) }.toMutableList()
        is ExprNode.Identifier -> resolveIdentifier(node.name)
        is ExprNode.BinaryOp -> evalBinary(node)
        is ExprNode.UnaryOp -> evalUnary(node)
        is ExprNode.Ternary -> if (toBool(evaluate(node.condition))) evaluate(node.ifTrue) else evaluate(node.ifFalse)
        is ExprNode.NullCoalesce -> evaluate(node.left) ?: evaluate(node.right)
        is ExprNode.MemberAccess -> evalMemberAccess(node)
        is ExprNode.IndexAccess -> evalIndexAccess(node)
        is ExprNode.MethodCall -> evalMethodCall(node)
        is ExprNode.FunctionCall -> evalFunctionCall(node)
    }

    private fun resolveIdentifier(name: String): Any? {
        return state[name]
    }

    private fun evalBinary(node: ExprNode.BinaryOp): Any? {
        val left = evaluate(node.left)
        val right = evaluate(node.right)

        return when (node.op) {
            "+" -> {
                if (left is String || right is String) {
                    toStr(left) + toStr(right)
                } else {
                    toNum(left) + toNum(right)
                }
            }
            "-" -> toNum(left) - toNum(right)
            "*" -> toNum(left) * toNum(right)
            "/" -> {
                val r = toNum(right)
                if (r == 0.0) Double.NaN else toNum(left) / r
            }
            "%" -> {
                val r = toNum(right)
                if (r == 0.0) Double.NaN else toNum(left) % r
            }
            "==" -> looseEquals(left, right)
            "!=" -> !looseEquals(left, right)
            "<" -> toNum(left) < toNum(right)
            ">" -> toNum(left) > toNum(right)
            "<=" -> toNum(left) <= toNum(right)
            ">=" -> toNum(left) >= toNum(right)
            "&&" -> toBool(left) && toBool(right)
            "||" -> toBool(left) || toBool(right)
            else -> throw ExpressionException("Unknown operator: ${node.op}")
        }
    }

    private fun evalUnary(node: ExprNode.UnaryOp): Any? {
        val operand = evaluate(node.operand)
        return when (node.op) {
            "!" -> !toBool(operand)
            "-" -> -toNum(operand)
            else -> throw ExpressionException("Unknown unary operator: ${node.op}")
        }
    }

    private fun evalMemberAccess(node: ExprNode.MemberAccess): Any? {
        val obj = evaluate(node.obj)
        return when {
            obj is String && node.property == "length" -> obj.length.toDouble()
            obj is List<*> && node.property == "length" -> obj.size.toDouble()
            obj is Map<*, *> -> obj[node.property]
            else -> null
        }
    }

    private fun evalIndexAccess(node: ExprNode.IndexAccess): Any? {
        val obj = evaluate(node.obj)
        val index = evaluate(node.index)
        return when {
            obj is List<*> && index is Number -> {
                val i = index.toInt()
                if (i in obj.indices) obj[i] else null
            }
            obj is Map<*, *> && index is String -> obj[index]
            obj is String && index is Number -> {
                val i = index.toInt()
                if (i in obj.indices) obj[i].toString() else null
            }
            else -> null
        }
    }

    private fun evalMethodCall(node: ExprNode.MethodCall): Any? {
        val obj = evaluate(node.obj)
        val args = node.args.map { evaluate(it) }

        // String methods
        if (obj is String) {
            return when (node.method) {
                "trim" -> obj.trim()
                "toUpperCase" -> obj.uppercase()
                "toLowerCase" -> obj.lowercase()
                "includes" -> {
                    val search = args.getOrNull(0)?.toString() ?: return false
                    obj.contains(search)
                }
                "replace" -> {
                    val search = args.getOrNull(0)?.toString() ?: return obj
                    val replacement = args.getOrNull(1)?.toString() ?: return obj
                    obj.replace(search, replacement)
                }
                "substring" -> {
                    val start = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                    val end = (args.getOrNull(1) as? Number)?.toInt() ?: obj.length
                    obj.substring(start.coerceIn(0, obj.length), end.coerceIn(0, obj.length))
                }
                "split" -> {
                    val delimiter = args.getOrNull(0)?.toString() ?: return listOf(obj)
                    obj.split(delimiter).toMutableList()
                }
                "startsWith" -> {
                    val prefix = args.getOrNull(0)?.toString() ?: return false
                    obj.startsWith(prefix)
                }
                "endsWith" -> {
                    val suffix = args.getOrNull(0)?.toString() ?: return false
                    obj.endsWith(suffix)
                }
                else -> null
            }
        }

        // Array methods
        if (obj is List<*>) {
            return when (node.method) {
                "includes" -> {
                    val search = args.getOrNull(0)
                    obj.any { looseEquals(it, search) }
                }
                "indexOf" -> {
                    val search = args.getOrNull(0)
                    val idx = obj.indexOfFirst { looseEquals(it, search) }
                    idx.toDouble()
                }
                "join" -> {
                    val separator = args.getOrNull(0)?.toString() ?: ","
                    obj.joinToString(separator) { toStr(it) }
                }
                "slice" -> {
                    val start = (args.getOrNull(0) as? Number)?.toInt() ?: 0
                    val end = (args.getOrNull(1) as? Number)?.toInt() ?: obj.size
                    val s = start.coerceIn(0, obj.size)
                    val e = end.coerceIn(0, obj.size)
                    obj.subList(s, e).toMutableList()
                }
                else -> null
            }
        }

        return null
    }

    private fun evalFunctionCall(node: ExprNode.FunctionCall): Any? {
        val args = node.args.map { evaluate(it) }
        return when (node.name) {
            "abs" -> abs(toNum(args.getOrNull(0)))
            "ceil" -> ceil(toNum(args.getOrNull(0)))
            "floor" -> floor(toNum(args.getOrNull(0)))
            "round" -> toNum(args.getOrNull(0)).roundToLong().toDouble()
            "min" -> {
                if (args.isEmpty()) Double.NaN
                else args.minOf { toNum(it) }
            }
            "max" -> {
                if (args.isEmpty()) Double.NaN
                else args.maxOf { toNum(it) }
            }
            "toString" -> toStr(args.getOrNull(0))
            "toNumber" -> toNum(args.getOrNull(0))
            else -> throw ExpressionException("Unknown function: ${node.name}")
        }
    }

    companion object {
        fun toBool(value: Any?): Boolean = when (value) {
            null -> false
            is Boolean -> value
            is Number -> value.toDouble() != 0.0 && !value.toDouble().isNaN()
            is String -> value.isNotEmpty()
            is List<*> -> value.isNotEmpty()
            is Map<*, *> -> value.isNotEmpty()
            else -> true
        }

        fun toNum(value: Any?): Double = when (value) {
            null -> 0.0
            is Number -> value.toDouble()
            is Boolean -> if (value) 1.0 else 0.0
            is String -> value.toDoubleOrNull() ?: Double.NaN
            else -> Double.NaN
        }

        fun toStr(value: Any?): String = when (value) {
            null -> "null"
            is Double -> {
                if (value == value.toLong().toDouble()) value.toLong().toString()
                else value.toString()
            }
            is List<*> -> value.joinToString(",") { toStr(it) }
            else -> value.toString()
        }

        fun looseEquals(a: Any?, b: Any?): Boolean {
            if (a == null && b == null) return true
            if (a == null || b == null) return false
            if (a is Number && b is Number) return a.toDouble() == b.toDouble()
            if (a is String && b is Number) return a.toDoubleOrNull() == b.toDouble()
            if (a is Number && b is String) return a.toDouble() == b.toDoubleOrNull()
            return a == b
        }

        fun parseAndEvaluate(expression: String, state: Map<String, Any?>): Any? {
            val tokens = Tokenizer(expression).tokenize()
            val ast = ExpressionParser(tokens).parse()
            return Evaluator(state).evaluate(ast)
        }
    }
}

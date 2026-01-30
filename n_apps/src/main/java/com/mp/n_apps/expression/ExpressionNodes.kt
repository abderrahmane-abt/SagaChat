package com.mp.n_apps.expression

sealed class ExprNode {
    data class NumberLiteral(val value: Double) : ExprNode()
    data class StringLiteral(val value: String) : ExprNode()
    data class BooleanLiteral(val value: Boolean) : ExprNode()
    data object NullLiteral : ExprNode()
    data class ArrayLiteral(val elements: List<ExprNode>) : ExprNode()

    data class Identifier(val name: String) : ExprNode()

    data class BinaryOp(val op: String, val left: ExprNode, val right: ExprNode) : ExprNode()
    data class UnaryOp(val op: String, val operand: ExprNode) : ExprNode()

    data class Ternary(val condition: ExprNode, val ifTrue: ExprNode, val ifFalse: ExprNode) : ExprNode()
    data class NullCoalesce(val left: ExprNode, val right: ExprNode) : ExprNode()

    data class MemberAccess(val obj: ExprNode, val property: String) : ExprNode()
    data class IndexAccess(val obj: ExprNode, val index: ExprNode) : ExprNode()

    data class MethodCall(val obj: ExprNode, val method: String, val args: List<ExprNode>) : ExprNode()
    data class FunctionCall(val name: String, val args: List<ExprNode>) : ExprNode()
}

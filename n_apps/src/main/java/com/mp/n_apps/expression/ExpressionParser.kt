package com.mp.n_apps.expression

class ExpressionParser(private val tokens: List<Token>) {
    private var pos = 0

    fun parse(): ExprNode {
        val result = parseTernary()
        if (current().type != TokenType.EOF) {
            throw ExpressionException("Unexpected token '${current().value}' at position ${current().pos}")
        }
        return result
    }

    private fun current(): Token = tokens[pos]

    private fun advance(): Token {
        val t = tokens[pos]
        if (pos < tokens.size - 1) pos++
        return t
    }

    private fun expect(type: TokenType): Token {
        val t = current()
        if (t.type != type) {
            throw ExpressionException("Expected $type but got '${t.value}' at position ${t.pos}")
        }
        return advance()
    }

    // ternary: nullCoalesce ('?' ternary ':' ternary)?
    private fun parseTernary(): ExprNode {
        val cond = parseNullCoalesce()
        if (current().type == TokenType.QUESTION) {
            advance()
            val ifTrue = parseTernary()
            expect(TokenType.COLON)
            val ifFalse = parseTernary()
            return ExprNode.Ternary(cond, ifTrue, ifFalse)
        }
        return cond
    }

    // nullCoalesce: or ('??' or)*
    private fun parseNullCoalesce(): ExprNode {
        var left = parseOr()
        while (current().type == TokenType.NULL_COALESCE) {
            advance()
            val right = parseOr()
            left = ExprNode.NullCoalesce(left, right)
        }
        return left
    }

    // or: and ('||' and)*
    private fun parseOr(): ExprNode {
        var left = parseAnd()
        while (current().type == TokenType.OR) {
            advance()
            val right = parseAnd()
            left = ExprNode.BinaryOp("||", left, right)
        }
        return left
    }

    // and: equality ('&&' equality)*
    private fun parseAnd(): ExprNode {
        var left = parseEquality()
        while (current().type == TokenType.AND) {
            advance()
            val right = parseEquality()
            left = ExprNode.BinaryOp("&&", left, right)
        }
        return left
    }

    // equality: comparison (('=='|'!=') comparison)*
    private fun parseEquality(): ExprNode {
        var left = parseComparison()
        while (current().type in setOf(TokenType.EQ, TokenType.NEQ)) {
            val op = advance().value
            val right = parseComparison()
            left = ExprNode.BinaryOp(op, left, right)
        }
        return left
    }

    // comparison: addition (('<'|'>'|'<='|'>=') addition)*
    private fun parseComparison(): ExprNode {
        var left = parseAddition()
        while (current().type in setOf(TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE)) {
            val op = advance().value
            val right = parseAddition()
            left = ExprNode.BinaryOp(op, left, right)
        }
        return left
    }

    // addition: multiplication (('+'|'-') multiplication)*
    private fun parseAddition(): ExprNode {
        var left = parseMultiplication()
        while (current().type in setOf(TokenType.PLUS, TokenType.MINUS)) {
            val op = advance().value
            val right = parseMultiplication()
            left = ExprNode.BinaryOp(op, left, right)
        }
        return left
    }

    // multiplication: unary (('*'|'/'|'%') unary)*
    private fun parseMultiplication(): ExprNode {
        var left = parseUnary()
        while (current().type in setOf(TokenType.STAR, TokenType.SLASH, TokenType.PERCENT)) {
            val op = advance().value
            val right = parseUnary()
            left = ExprNode.BinaryOp(op, left, right)
        }
        return left
    }

    // unary: ('!'|'-') unary | postfix
    private fun parseUnary(): ExprNode {
        if (current().type == TokenType.NOT) {
            advance()
            return ExprNode.UnaryOp("!", parseUnary())
        }
        if (current().type == TokenType.MINUS) {
            advance()
            return ExprNode.UnaryOp("-", parseUnary())
        }
        return parsePostfix()
    }

    // postfix: primary (('.' ident args?) | ('[' expr ']'))*
    private fun parsePostfix(): ExprNode {
        var node = parsePrimary()
        while (true) {
            when (current().type) {
                TokenType.DOT -> {
                    advance()
                    val name = expect(TokenType.IDENTIFIER).value
                    if (current().type == TokenType.LPAREN) {
                        advance()
                        val args = parseArgList()
                        expect(TokenType.RPAREN)
                        node = ExprNode.MethodCall(node, name, args)
                    } else {
                        node = ExprNode.MemberAccess(node, name)
                    }
                }
                TokenType.LBRACKET -> {
                    advance()
                    val index = parseTernary()
                    expect(TokenType.RBRACKET)
                    node = ExprNode.IndexAccess(node, index)
                }
                else -> break
            }
        }
        return node
    }

    // primary: number | string | boolean | null | '(' expr ')' | '[' list ']' | ident ('(' args ')')?
    private fun parsePrimary(): ExprNode {
        return when (current().type) {
            TokenType.NUMBER -> {
                val v = advance().value
                ExprNode.NumberLiteral(v.toDouble())
            }
            TokenType.STRING -> {
                ExprNode.StringLiteral(advance().value)
            }
            TokenType.BOOLEAN -> {
                ExprNode.BooleanLiteral(advance().value == "true")
            }
            TokenType.NULL -> {
                advance()
                ExprNode.NullLiteral
            }
            TokenType.LPAREN -> {
                advance()
                val expr = parseTernary()
                expect(TokenType.RPAREN)
                expr
            }
            TokenType.LBRACKET -> {
                advance()
                val elements = if (current().type == TokenType.RBRACKET) {
                    emptyList()
                } else {
                    parseArgList()
                }
                expect(TokenType.RBRACKET)
                ExprNode.ArrayLiteral(elements)
            }
            TokenType.IDENTIFIER -> {
                val name = advance().value
                if (current().type == TokenType.LPAREN) {
                    advance()
                    val args = parseArgList()
                    expect(TokenType.RPAREN)
                    ExprNode.FunctionCall(name, args)
                } else {
                    ExprNode.Identifier(name)
                }
            }
            else -> throw ExpressionException("Unexpected token '${current().value}' at position ${current().pos}")
        }
    }

    private fun parseArgList(): List<ExprNode> {
        val args = mutableListOf<ExprNode>()
        if (current().type != TokenType.RPAREN && current().type != TokenType.RBRACKET) {
            args.add(parseTernary())
            while (current().type == TokenType.COMMA) {
                advance()
                args.add(parseTernary())
            }
        }
        return args
    }
}

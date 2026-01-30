package com.mp.n_apps.expression

enum class TokenType {
    NUMBER, STRING, BOOLEAN, NULL, IDENTIFIER,
    PLUS, MINUS, STAR, SLASH, PERCENT,
    EQ, NEQ, LT, GT, LTE, GTE,
    AND, OR, NOT,
    QUESTION, COLON, NULL_COALESCE,
    LPAREN, RPAREN, LBRACKET, RBRACKET,
    DOT, COMMA,
    EOF
}

data class Token(val type: TokenType, val value: String, val pos: Int)

class Tokenizer(private val input: String) {
    private var pos = 0

    fun tokenize(): List<Token> {
        val tokens = mutableListOf<Token>()
        while (pos < input.length) {
            skipWhitespace()
            if (pos >= input.length) break

            val start = pos
            val c = input[pos]
            val token = when {
                c.isDigit() || (c == '.' && pos + 1 < input.length && input[pos + 1].isDigit()) -> readNumber(start)
                c == '\'' || c == '"' -> readString(start)
                c == '+' -> single(TokenType.PLUS, start)
                c == '-' -> single(TokenType.MINUS, start)
                c == '*' -> single(TokenType.STAR, start)
                c == '/' -> single(TokenType.SLASH, start)
                c == '%' -> single(TokenType.PERCENT, start)
                c == '(' -> single(TokenType.LPAREN, start)
                c == ')' -> single(TokenType.RPAREN, start)
                c == '[' -> single(TokenType.LBRACKET, start)
                c == ']' -> single(TokenType.RBRACKET, start)
                c == '.' -> single(TokenType.DOT, start)
                c == ',' -> single(TokenType.COMMA, start)
                c == ':' -> single(TokenType.COLON, start)
                c == '?' && peek() == '?' -> { pos += 2; Token(TokenType.NULL_COALESCE, "??", start) }
                c == '?' -> single(TokenType.QUESTION, start)
                c == '!' && peek() == '=' -> { pos += 2; Token(TokenType.NEQ, "!=", start) }
                c == '!' -> single(TokenType.NOT, start)
                c == '=' && peek() == '=' -> { pos += 2; Token(TokenType.EQ, "==", start) }
                c == '<' && peek() == '=' -> { pos += 2; Token(TokenType.LTE, "<=", start) }
                c == '<' -> single(TokenType.LT, start)
                c == '>' && peek() == '=' -> { pos += 2; Token(TokenType.GTE, ">=", start) }
                c == '>' -> single(TokenType.GT, start)
                c == '&' && peek() == '&' -> { pos += 2; Token(TokenType.AND, "&&", start) }
                c == '|' && peek() == '|' -> { pos += 2; Token(TokenType.OR, "||", start) }
                c.isLetter() || c == '_' -> readIdentifier(start)
                else -> throw ExpressionException("Unexpected character '$c' at position $pos")
            }
            tokens.add(token)
        }
        tokens.add(Token(TokenType.EOF, "", pos))
        return tokens
    }

    private fun skipWhitespace() {
        while (pos < input.length && input[pos].isWhitespace()) pos++
    }

    private fun peek(): Char? = if (pos + 1 < input.length) input[pos + 1] else null

    private fun single(type: TokenType, start: Int): Token {
        pos++
        return Token(type, input[start].toString(), start)
    }

    private fun readNumber(start: Int): Token {
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        return Token(TokenType.NUMBER, input.substring(start, pos), start)
    }

    private fun readString(start: Int): Token {
        val quote = input[pos]
        pos++
        val sb = StringBuilder()
        while (pos < input.length && input[pos] != quote) {
            if (input[pos] == '\\' && pos + 1 < input.length) {
                pos++
                when (input[pos]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    '\\' -> sb.append('\\')
                    '\'' -> sb.append('\'')
                    '"' -> sb.append('"')
                    else -> { sb.append('\\'); sb.append(input[pos]) }
                }
            } else {
                sb.append(input[pos])
            }
            pos++
        }
        if (pos >= input.length) throw ExpressionException("Unterminated string starting at position $start")
        pos++ // closing quote
        return Token(TokenType.STRING, sb.toString(), start)
    }

    private fun readIdentifier(start: Int): Token {
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
        val text = input.substring(start, pos)
        return when (text) {
            "true" -> Token(TokenType.BOOLEAN, "true", start)
            "false" -> Token(TokenType.BOOLEAN, "false", start)
            "null" -> Token(TokenType.NULL, "null", start)
            else -> Token(TokenType.IDENTIFIER, text, start)
        }
    }
}

class ExpressionException(message: String) : Exception(message)

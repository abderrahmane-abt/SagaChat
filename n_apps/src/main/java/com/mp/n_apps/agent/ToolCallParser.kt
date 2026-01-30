package com.mp.n_apps.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

object ToolCallParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val TOOL_CALL_REGEX = Regex(
        "<tool_call>\\s*(\\{.*?\\})\\s*</tool_call>",
        RegexOption.DOT_MATCHES_ALL
    )
    private val FENCED_JSON_REGEX = Regex(
        "```(state\\.json|ui\\.json|actions\\.json)\\s*\\n(.*?)\\n```",
        RegexOption.DOT_MATCHES_ALL
    )

    fun parseToolCalls(text: String): List<ToolCall> {
        return TOOL_CALL_REGEX.findAll(text).mapNotNull { match ->
            try {
                json.decodeFromString<ToolCall>(match.groupValues[1].trim())
            } catch (_: Exception) {
                null
            }
        }.toList()
    }

    /**
     * Fallback parser: if the model outputs labeled JSON fences
     * (```state.json ... ```) instead of <tool_call> XML, convert them
     * into synthetic write_file tool calls so the agent loop continues.
     */
    fun parseFallbackFencedJson(text: String): List<ToolCall> {
        val calls = mutableListOf<ToolCall>()
        for (match in FENCED_JSON_REGEX.findAll(text)) {
            val fileName = match.groupValues[1]
            val content = match.groupValues[2].trim()
            if (content.startsWith("{") || content.startsWith("[")) {
                try {
                    val parsed = json.parseToJsonElement(content)
                    calls.add(
                        ToolCall(
                            name = "write_file",
                            params = JsonObject(
                                mapOf(
                                    "file" to JsonPrimitive(fileName),
                                    "content" to parsed
                                )
                            )
                        )
                    )
                } catch (_: Exception) {
                    // Invalid JSON in fence — skip
                }
            }
        }
        return calls
    }

    fun extractFinalText(text: String): String {
        return text
            .replace(TOOL_CALL_REGEX, "")
            .replace(FENCED_JSON_REGEX, "")
            .trim()
    }

    fun formatToolResult(result: ToolResult): String {
        val dataStr = if (result.success) {
            result.data?.toString() ?: """{"success": true}"""
        } else {
            """{"success": false, "error": "${result.error ?: "Unknown error"}"}"""
        }
        return """<tool_result name="${result.name}">
$dataStr
</tool_result>"""
    }
}

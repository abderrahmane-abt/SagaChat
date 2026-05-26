package com.moorixlabs.sagachat.viewmodel.home_vm

import android.util.Log
import com.moorixlabs.sagachat.model.ChatMessage
import com.moorixlabs.sagachat.model.MemoryMetrics
import com.moorixlabs.sagachat.model.MessageKind
import com.moorixlabs.sagachat.model.TextMetrics
import com.moorixlabs.sagachat.repo.ChatRepository
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "InferenceCoordinator"
private const val MAX_TOOL_ITERATIONS = 3
private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private const val ROLE_TOOL = "tool"
private val THINK_TAGS = listOf(
    "<think>" to "</think>",
    "[THINK]" to "[/THINK]",
    "<reasoning>" to "</reasoning>",
)

data class GenerationOutcome(
    val content: String,
    val thinking: String,
    val error: String?,
    val textMetrics: TextMetrics?,
    val memoryMetrics: MemoryMetrics?,
)

@Singleton
class InferenceCoordinator @Inject constructor(
    private val chatRepo: ChatRepository,
    private val toolCallCoordinator: ToolCallCoordinator,
    private val modelSession: ModelSessionManager,
) {
    suspend fun run(
        chatId: String,
        onStreamingUpdate: (content: String, thinking: String) -> Unit,
        onToolExecuted: (List<ChatMessage>) -> Unit,
        onMetrics: (TextMetrics?, MemoryMetrics?) -> Unit,
    ): GenerationOutcome {
        var cleanContent = ""
        var thinkingContent = ""
        var errorMessage: String? = null
        var textMetrics: TextMetrics? = null
        var memoryMetrics: MemoryMetrics? = null
        val toolResults = mutableListOf<Pair<String, Boolean>>()

        for (iteration in 0 until MAX_TOOL_ITERATIONS) {
            var rawAssistant = ""
            var pendingToolCall: InferenceEvent.ToolCall? = null

            val messages = chatRepo.getMessages(chatId)
            val historyJson = buildMessagesJson(messages)

            InferenceClient.generateMultiTurn(historyJson, modelSession.maxTokens.value)
                .transformWhile { event ->
                    emit(event)
                    event !is InferenceEvent.Done && event !is InferenceEvent.Error
                }
                .collect { event ->
                    when (event) {
                        is InferenceEvent.Token -> {
                            rawAssistant += event.text
                            val (clean, think) = parseThinking(rawAssistant)
                            cleanContent = clean
                            thinkingContent = think
                            onStreamingUpdate(cleanContent, thinkingContent)
                        }
                        is InferenceEvent.ToolCall -> pendingToolCall = event
                        is InferenceEvent.Metrics -> {
                            val parsed = parseMetrics(event.metricsJson)
                            textMetrics = parsed.first
                            memoryMetrics = parsed.second
                            onMetrics(parsed.first, parsed.second)
                        }
                        is InferenceEvent.Error -> errorMessage = event.message
                        else -> {}
                    }
                }

            val tc = pendingToolCall ?: toolCallCoordinator.parseFromText(cleanContent)
            if (tc == null || errorMessage != null) break

            Log.i(TAG, "tool call fired: name=${tc.name} args=${tc.argsJson.take(200)}")

            val toolMsg = toolCallCoordinator.execute(chatId, tc)
            val isSuccess = !toolMsg.content.contains("\"error\"")
            toolResults += toolMsg.id to isSuccess
            chatRepo.addMessage(toolMsg)
            onToolExecuted(chatRepo.getMessages(chatId))
            onStreamingUpdate("", "")
            cleanContent = ""
            thinkingContent = ""

            if (isSuccess) InferenceClient.clearTools()
        }

        // Keep only the first successful tool result; remove duplicates
        val firstSuccessId = toolResults.firstOrNull { it.second }?.first
        if (firstSuccessId != null) {
            toolResults
                .filter { it.first != firstSuccessId }
                .forEach { chatRepo.deleteMessage(it.first) }
            onToolExecuted(chatRepo.getMessages(chatId))
        }

        return GenerationOutcome(
            content = cleanContent,
            thinking = thinkingContent,
            error = errorMessage,
            textMetrics = textMetrics,
            memoryMetrics = memoryMetrics,
        )
    }

    private fun buildMessagesJson(messages: List<ChatMessage>): String {
        val arr = JSONArray()
        messages.forEach { msg ->
            if (msg.archivedByCompactId != null) return@forEach
            if (msg.kind == MessageKind.CompactSummary) {
                arr.put(JSONObject().apply {
                    put("role", ROLE_ASSISTANT)
                    put("content", msg.content)
                })
                return@forEach
            }
            when (msg.role) {
                ROLE_USER, ROLE_ASSISTANT, ROLE_TOOL -> {
                    arr.put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                        if (msg.role == ROLE_TOOL && msg.modelName.isNotEmpty()) {
                            put("name", msg.modelName)
                        }
                    })
                }
            }
        }
        return arr.toString()
    }

    private fun parseMetrics(json: String): Pair<TextMetrics?, MemoryMetrics?> {
        if (json.isBlank()) return null to null
        return try {
            val o = JSONObject(json)
            val text = TextMetrics(
                tokensPerSecond = o.optDouble("tokensPerSecond", 0.0),
                timeToFirstTokenMs = o.optLong("timeToFirstTokenMs", 0L),
                totalTimeMs = o.optLong("totalTimeMs", 0L),
                promptTokens = o.optInt("tokensEvaluated", 0),
                generatedTokens = o.optInt("tokensPredicted", 0),
            )
            val currentRss = readCurrentRssMb()
            val mem = MemoryMetrics(
                modelSizeMB = o.optDouble("modelSizeMB", 0.0),
                contextSizeMB = o.optDouble("contextSizeMB", 0.0),
                peakMemoryMB = o.optDouble("peakMemoryMB", 0.0),
                currentMemoryMB = currentRss,
                usagePercent = o.optDouble("memoryUsagePercent", 0.0),
            )
            val textValid = text.tokensPerSecond > 0.0 || text.generatedTokens > 0
            val memValid = mem.modelSizeMB > 0.0 || mem.peakMemoryMB > 0.0
            (if (textValid) text else null) to (if (memValid) mem else null)
        } catch (_: Exception) {
            null to null
        }
    }

    private fun readCurrentRssMb(): Double {
        val raw = InferenceClient.getMemoryStatsJson() ?: return 0.0
        return runCatching { JSONObject(raw).optDouble("current_rss_mb", 0.0) }.getOrDefault(0.0)
    }

    private fun parseThinking(raw: String): Pair<String, String> {
        val content = StringBuilder()
        val thinking = StringBuilder()
        var i = 0
        while (i < raw.length) {
            val next = THINK_TAGS
                .map { (open, _) -> raw.indexOf(open, i) to open }
                .filter { it.first >= 0 }
                .minByOrNull { it.first }
            if (next == null) {
                content.append(raw, i, raw.length)
                break
            }
            val (start, openTag) = next
            content.append(raw, i, start)
            val closeTag = THINK_TAGS.first { it.first == openTag }.second
            val bodyStart = start + openTag.length
            val end = raw.indexOf(closeTag, bodyStart)
            if (end < 0) {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(raw, bodyStart, raw.length)
                i = raw.length
            } else {
                if (thinking.isNotEmpty()) thinking.append("\n\n")
                thinking.append(raw, bodyStart, end)
                i = end + closeTag.length
            }
        }
        return content.toString().trim() to thinking.toString().trim()
    }
}

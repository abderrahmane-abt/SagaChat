package com.moorixlabs.sagachat.repo

import com.moorixlabs.sagachat.model.ChatMessage
import com.moorixlabs.sagachat.model.MemoryState
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.service.inference.InferenceEvent
import kotlinx.coroutines.flow.transformWhile
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val SUMMARIZE_EVERY_N_TURNS = 8

@Singleton
class MemoryManager @Inject constructor(
    private val memoryRepo: MemoryRepository,
) {
    fun get(characterId: String): MemoryState = memoryRepo.get(characterId)

    suspend fun maybeUpdateMemory(
        characterId: String,
        allMessages: List<ChatMessage>,
        charName: String,
        userName: String,
    ): MemoryState {
        val current = memoryRepo.get(characterId)
        val turnCount = allMessages.count { it.role == "user" }

        val shouldSummarize = turnCount > 0
            && turnCount % SUMMARIZE_EVERY_N_TURNS == 0
            && turnCount > current.lastSummarizedTurnIndex

        if (!shouldSummarize) return current

        val window = allMessages.takeLast(SUMMARIZE_EVERY_N_TURNS * 2)
        val conversationText = window.joinToString("\n") { msg ->
            val speaker = if (msg.role == "user") userName else charName
            "$speaker: ${msg.content.trim()}"
        }

        val summarizePrompt = buildSummarizePrompt(
            conversationText, charName, userName, current.entityJson,
        )
        val summary = runInferenceSummary(summarizePrompt)
        if (summary.isBlank()) return current

        val updatedEntities = extractEntities(
            summary = summary,
            existing = current.entityJson,
            charName = charName,
            userName = userName,
            messages = window,
        )

        val updated = current.copy(
            summary = summary,
            entityJson = updatedEntities,
            lastSummarizedTurnIndex = turnCount,
            updatedAt = System.currentTimeMillis(),
        )
        memoryRepo.save(updated)
        return updated
    }

    fun resetMemory(characterId: String) {
        memoryRepo.delete(characterId)
    }

    private fun buildSummarizePrompt(
        conversationText: String,
        charName: String,
        userName: String,
        existingEntityJson: String,
    ): String = buildString {
        append("<|im_start|>system\n")
        append("You are a story summarizer. Summarize the key events, emotional shifts, ")
        append("and important facts from this conversation excerpt. ")
        append("Be concise — 3 to 5 bullet points, past tense, third person. ")
        append("Focus on: relationship changes, revelations, decisions, and location changes. ")
        append("Never include your own commentary.\n")
        append("<|im_end|>\n")
        append("<|im_start|>user\n")
        append("Characters: $charName and $userName.\n\n")
        append("Conversation:\n$conversationText\n\n")
        append("Write a 3–5 bullet summary.\n")
        append("<|im_end|>\n")
        append("<|im_start|>assistant\n")
    }

    private suspend fun runInferenceSummary(prompt: String): String {
        val result = StringBuilder()
        // InferenceClient.generate is the raw prompt generation path.
        InferenceClient.generate(prompt, maxTokens = 256)
            .transformWhile { event ->
                emit(event)
                event !is InferenceEvent.Done && event !is InferenceEvent.Error
            }
            .collect { event ->
                if (event is InferenceEvent.Token) result.append(event.text)
            }
        return result.toString().trim()
    }

    private fun extractEntities(
        summary: String,
        existing: String,
        charName: String,
        userName: String,
        messages: List<ChatMessage>,
    ): String {
        val obj = runCatching { JSONObject(existing) }.getOrDefault(JSONObject())

        // Store user name if not yet tracked
        if (obj.optString("user_name", "").isBlank()) {
            obj.put("user_name", userName)
        }

        // Detect mood keywords in last assistant message
        val lastAssistant = messages.lastOrNull { it.role == "assistant" }?.content.orEmpty()
        val mood = when {
            Regex("\\b(angry|furious|yell|scream)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lastAssistant) -> "angry"
            Regex("\\b(soft|gentle|warm|smil)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lastAssistant) -> "softening"
            Regex("\\b(cold|distant|ignore|silent)\\b", RegexOption.IGNORE_CASE).containsMatchIn(lastAssistant) -> "cold"
            else -> obj.optString("char_mood", "neutral")
        }
        obj.put("char_mood", mood)

        // Append summary bullets as new key_events; keep last 5
        val events = obj.optJSONArray("key_events") ?: JSONArray()
        val cleanSummary = summary.lines()
            .filter { it.startsWith("-") || it.startsWith("•") }
            .take(2)
            .joinToString("; ") { it.trimStart('-', '•', ' ') }
        if (cleanSummary.isNotBlank()) {
            events.put(cleanSummary)
            val kept = JSONArray()
            val start = maxOf(0, events.length() - 5)
            for (i in start until events.length()) kept.put(events.getString(i))
            obj.put("key_events", kept)
        }

        return obj.toString()
    }
}

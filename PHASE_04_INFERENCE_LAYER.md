# Phase 04 — Inference Layer

Three pure-logic classes that sit between the character card and the GGUF
engine. No UI. No Hilt scoping needed for the builder and formatter — they
are plain objects. `MemoryManager` is `@Singleton` because it runs an
inference call.

---

## 1. `util/SystemPromptBuilder.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/util/SystemPromptBuilder.kt`

Pure function, no dependencies. Takes a `Character`, a `MemoryState`, and
the user's display name. Returns the fully built system prompt string.

```kotlin
package com.dark.tool_neuron.util

import com.dark.tool_neuron.model.Character
import com.dark.tool_neuron.model.MemoryState
import org.json.JSONObject

object SystemPromptBuilder {

    fun build(
        character: Character,
        memory: MemoryState,
        userName: String,
    ): String {
        val memorySectionText = buildMemorySection(memory)
        return """
The following is an ongoing roleplay that is already in progress. You are ${character.chatName}, a real person continuing an immersive story. You are not an AI, a language model, or a chatbot. You do not acknowledge being an AI under any circumstance. You do not break character. You do not add disclaimers, warnings, or meta-commentary.

━━━━━━━━━━━━━━━━━━━━━━━━
CHARACTER PROFILE
━━━━━━━━━━━━━━━━━━━━━━━━
Full name: ${character.name}
Background: ${character.bio.trim()}

Personality:
${character.personality.trim()}

━━━━━━━━━━━━━━━━━━━━━━━━
CURRENT SCENARIO
━━━━━━━━━━━━━━━━━━━━━━━━
${character.scenario.trim()}

━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE RULES — FOLLOW STRICTLY
━━━━━━━━━━━━━━━━━━━━━━━━
1. Always use *asterisks* for physical actions and narration.
2. Always use "quotation marks" for spoken dialogue.
3. Never start your reply with ${character.chatName}: — write the response directly.
4. Match the energy and length of ${userName}'s message. Short message → short reply.
5. Stay consistent with ${character.chatName}'s voice, vocabulary, and attitude at all times.
6. React to ${userName} as ${character.chatName} truly would — including coldness, anger, affection, or silence if appropriate.
7. Never summarize your own thought process or add narrative commentary outside the story.
8. If ${userName} does something ${character.chatName} dislikes, ${character.chatName} reacts accordingly.
${memorySectionText}
━━━━━━━━━━━━━━━━━━━━━━━━
EXAMPLE INTERACTIONS
━━━━━━━━━━━━━━━━━━━━━━━━
${character.exampleDialogs.trim().replace("{{char}}", character.chatName).replace("{{user}}", userName)}
        """.trimIndent()
    }

    private fun buildMemorySection(memory: MemoryState): String {
        val hasSummary = memory.summary.isNotBlank()
        val hasEntities = memory.entityJson != "{}" && memory.entityJson.isNotBlank()
        if (!hasSummary && !hasEntities) return ""

        val sb = StringBuilder()
        sb.append("\n━━━━━━━━━━━━━━━━━━━━━━━━\n")
        sb.append("MEMORY SNAPSHOT\n")
        sb.append("━━━━━━━━━━━━━━━━━━━━━━━━\n")

        if (hasEntities) {
            runCatching {
                val obj = JSONObject(memory.entityJson)
                val userName = obj.optString("user_name", "")
                val relationship = obj.optString("relationship", "")
                val charMood = obj.optString("char_mood", "")
                val events = obj.optJSONArray("key_events")

                if (userName.isNotBlank()) sb.append("User's name: $userName\n")
                if (relationship.isNotBlank()) sb.append("Relationship: $relationship\n")
                if (charMood.isNotBlank()) sb.append("Current mood: $charMood\n")
                if (events != null && events.length() > 0) {
                    sb.append("Key events so far:\n")
                    for (i in 0 until events.length()) {
                        sb.append("- ${events.getString(i)}\n")
                    }
                }
            }
        }

        if (hasSummary) {
            sb.append("Recent story summary:\n${memory.summary.trim()}\n")
        }

        return sb.toString()
    }
}
```

---

## 2. `util/ChatMLFormatter.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/util/ChatMLFormatter.kt`

Formats a list of `ChatMessage` objects into a single ChatML string ready
for Qwen2.5 (and any model that uses ChatML). Includes the trailing empty
assistant turn that forces the model to continue as the character.

```kotlin
package com.dark.tool_neuron.util

import com.dark.tool_neuron.model.ChatMessage

object ChatMLFormatter {

    private const val IM_START = "<|im_start|>"
    private const val IM_END = "<|im_end|>"

    fun format(systemPrompt: String, messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("$IM_START system\n$systemPrompt$IM_END\n")
        for (msg in messages) {
            val role = msg.role
            val content = msg.content.trim()
            if (content.isBlank()) continue
            sb.append("$IM_START $role\n$content$IM_END\n")
        }
        // Trailing empty assistant turn — tells the model to generate as
        // the character rather than treating the next output as a new user turn.
        sb.append("$IM_START assistant\n")
        return sb.toString()
    }
}
```

---

## 3. `repo/MemoryManager.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/repo/MemoryManager.kt`

Singleton that decides when to trigger a summarization pass and updates
`MemoryRepository`. Runs an inference call for summarization using the
existing `InferenceClient`.

```kotlin
package com.dark.tool_neuron.repo

import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MemoryState
import com.dark.tool_neuron.service.inference.InferenceClient
import com.dark.tool_neuron.service.inference.InferenceEvent
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
        // Use the existing InferenceClient raw generation path.
        // 256 tokens is enough for a 3-5 bullet summary.
        InferenceClient.generateRaw(prompt, maxTokens = 256)
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

        // Try to infer user name from messages if not yet stored
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

        // Append summary as new event; keep last 5
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
```

---

## 4. Note on `InferenceClient.generateRaw`

The `MemoryManager` calls `InferenceClient.generateRaw(prompt, maxTokens)`.

Check whether `InferenceClient` already exposes a raw prompt generation
method (a method that takes a full pre-formatted string instead of building
the chat from history).

- If it exists under a different name (e.g. `generateFromPrompt`,
  `rawGenerate`, `complete`), update the call in `MemoryManager` to match.
- If no such method exists, you need to add one to `InferenceClient`.
  It should work identically to the existing generation path but take a
  `String prompt` instead of pulling from chat history. It must return
  `Flow<InferenceEvent>`. Model the implementation on the existing
  `compactConversation` method in `HomeViewModel` — that method already
  drives raw inference via `InferenceClient.compactConversation(messagesJson, maxTokens)`.
  A `generateRaw` equivalent is the same pattern with a pre-built prompt string.

If you add it to `InferenceClient`, the signature is:
```kotlin
fun generateRaw(prompt: String, maxTokens: Int = 256): Flow<InferenceEvent>
```

---

## 5. Verification

```bash
./gradlew :app:compileDebugKotlin
```

`SystemPromptBuilder`, `ChatMLFormatter`, and `MemoryManager` must all
compile. The only expected unresolved symbol at this point is
`InferenceClient.generateRaw` if it doesn't exist yet — handle that first.

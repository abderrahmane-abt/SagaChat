package com.moorixlabs.sagachat.util

import com.moorixlabs.sagachat.model.Character
import com.moorixlabs.sagachat.model.MemoryState
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

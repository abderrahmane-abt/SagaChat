package com.moorixlabs.sagachat.util

import com.moorixlabs.sagachat.model.Character
import org.json.JSONArray
import org.json.JSONObject

object CharacterExporter {

    // Returns a pretty-printed SagaChat V1 JSON string for the given character.
    fun toJson(character: Character): String {
        return buildCharacterJson(character).toString(2)
    }

    // Returns a pretty-printed SagaChat V1 JSON string including chat history.
    fun exportSession(character: Character, messages: List<com.moorixlabs.sagachat.model.ChatMessage>): String {
        val obj = buildCharacterJson(character)
        val messagesArray = JSONArray()
        messages.forEach { msg ->
            val msgObj = JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
            }
            messagesArray.put(msgObj)
        }
        obj.put("messages", messagesArray)
        return obj.toString(2)
    }

    private fun buildCharacterJson(character: Character): JSONObject {
        return JSONObject().apply {
            put("sagachat_version", 1)
            put("name", character.name)
            put("chat_name", character.chatName)
            put("bio", character.bio)
            put("personality", character.personality)
            put("scenario", character.scenario)
            put("first_mes", character.initialMessage)
            put("mes_example", character.exampleDialogs)
            put("tags", JSONArray(character.tags))
        }
    }

    // Sanitizes the character name into a safe filename.
    fun fileName(character: Character): String {
        val safe = character.name
            .replace(Regex("[^a-zA-Z0-9_\\- ]"), "")
            .trim()
            .replace(" ", "_")
            .take(48)
            .ifBlank { "character" }
        return "${safe}.json"
    }
}

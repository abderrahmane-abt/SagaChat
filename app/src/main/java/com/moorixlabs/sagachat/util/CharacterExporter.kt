package com.moorixlabs.sagachat.util

import com.moorixlabs.sagachat.model.Character
import org.json.JSONArray
import org.json.JSONObject

object CharacterExporter {

    // Returns a pretty-printed SagaChat V1 JSON string for the given character.
    fun toJson(character: Character): String {
        val obj = JSONObject().apply {
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
        // Pretty-print with 2-space indent
        return obj.toString(2)
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

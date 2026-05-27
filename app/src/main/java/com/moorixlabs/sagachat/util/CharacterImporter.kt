package com.moorixlabs.sagachat.util

import com.moorixlabs.sagachat.model.Character
import org.json.JSONArray
import org.json.JSONObject

object CharacterImporter {

    sealed class ImportResult {
        data class Success(
            val character: Character,
            val detectedFormat: Format,
            val warnings: List<String> = emptyList(),
        ) : ImportResult()

        data class Failure(val reason: String) : ImportResult()
    }

    enum class Format {
        SAGACHAT_V1,
        TAVERN_V1,
        TAVERN_V2,
    }

    fun parse(json: String): ImportResult {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return ImportResult.Failure("JSON is empty.")

        val root = try {
            JSONObject(trimmed)
        } catch (e: Exception) {
            return ImportResult.Failure("Not valid JSON: ${e.message}")
        }

        return when {
            root.has("sagachat_version")               -> parseSagaChat(root)
            root.optString("spec") == "chara_card_v2" -> parseTavernV2(root)
            else                                        -> parseTavernV1(root)
        }
    }

    // ── SagaChat native ──────────────────────────────────────────────────

    private fun parseSagaChat(root: JSONObject): ImportResult {
        val name = root.optString("name").trim()
        if (name.isBlank()) return ImportResult.Failure("Field \"name\" is required.")

        val chatName = root.optString("chat_name").trim().ifBlank { name }
        val warnings = mutableListOf<String>()
        if (root.optString("chat_name").isBlank()) {
            warnings += "\"chat_name\" not found — using \"name\" as display name."
        }

        return ImportResult.Success(
            character = buildCharacter(
                name           = name,
                chatName       = chatName,
                bio            = root.optString("bio"),
                personality    = root.optString("personality"),
                scenario       = root.optString("scenario"),
                initialMessage = root.optString("first_mes"),
                exampleDialogs = root.optString("mes_example"),
                tags           = parseTagArray(root.optJSONArray("tags")),
            ),
            detectedFormat = Format.SAGACHAT_V1,
            warnings = warnings,
        )
    }

    // ── SillyTavern / TavernAI V1 ────────────────────────────────────────

    private fun parseTavernV1(root: JSONObject): ImportResult {
        val name = root.optString("name").trim()
        if (name.isBlank()) return ImportResult.Failure("Field \"name\" is required.")

        val chatName = root.optString("chat_name").trim().ifBlank { name }
        val warnings = mutableListOf<String>()
        if (root.optString("first_mes").isBlank()) {
            warnings += "No \"first_mes\" found — initial message will be empty."
        }

        return ImportResult.Success(
            character = buildCharacter(
                name           = name,
                chatName       = chatName,
                bio            = root.optString("description"),
                personality    = root.optString("personality"),
                scenario       = root.optString("scenario"),
                initialMessage = root.optString("first_mes"),
                exampleDialogs = root.optString("mes_example"),
                tags           = parseTagArray(root.optJSONArray("tags")),
            ),
            detectedFormat = Format.TAVERN_V1,
            warnings = warnings,
        )
    }

    // ── SillyTavern / TavernAI V2 ────────────────────────────────────────

    private fun parseTavernV2(root: JSONObject): ImportResult {
        val data = root.optJSONObject("data")
            ?: return ImportResult.Failure("V2 card is missing the \"data\" object.")

        val name = data.optString("name").trim()
        if (name.isBlank()) return ImportResult.Failure("Field \"data.name\" is required.")

        val chatName = data.optString("chat_name").trim().ifBlank { name }
        val warnings = mutableListOf<String>()
        if (data.has("character_book")) {
            warnings += "This card contains a character book (lorebook). Lorebooks are not yet supported and will be skipped."
        }

        return ImportResult.Success(
            character = buildCharacter(
                name           = name,
                chatName       = chatName,
                bio            = data.optString("description"),
                personality    = data.optString("personality"),
                scenario       = data.optString("scenario"),
                initialMessage = data.optString("first_mes"),
                exampleDialogs = data.optString("mes_example"),
                tags           = parseTagArray(data.optJSONArray("tags")),
            ),
            detectedFormat = Format.TAVERN_V2,
            warnings = warnings,
        )
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun buildCharacter(
        name: String,
        chatName: String,
        bio: String,
        personality: String,
        scenario: String,
        initialMessage: String,
        exampleDialogs: String,
        tags: List<String>,
    ) = Character(
        id             = "",      // assigned by CharacterRepository.create()
        name           = name,
        chatName       = chatName,
        bio            = bio,
        personality    = personality,
        scenario       = scenario,
        initialMessage = initialMessage,
        exampleDialogs = exampleDialogs,
        tags           = tags,
        avatarUri      = "",
        createdAt      = System.currentTimeMillis(),
        updatedAt      = System.currentTimeMillis(),
        linkedChatId   = "",
    )

    private fun parseTagArray(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        return (0 until arr.length())
            .mapNotNull { arr.optString(it).trim().lowercase().ifBlank { null } }
            .distinct()
    }
}

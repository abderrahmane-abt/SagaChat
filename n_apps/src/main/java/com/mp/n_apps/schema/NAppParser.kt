package com.mp.n_apps.schema

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

data class NApp(
    val manifest: NAppManifest,
    val stateSchema: NAppStateSchema,
    val ui: NAppUISchema,
    val actionsSchema: NAppActionsSchema
)

object NAppParser {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    fun parse(
        manifestJson: String? = null,
        stateJson: String? = null,
        uiJson: String,
        actionsJson: String? = null
    ): Result<NApp> = runCatching {
        val manifest = manifestJson?.let { json.decodeFromString<NAppManifest>(it) }
            ?: NAppManifest()
        val state = stateJson?.let { json.decodeFromString<NAppStateSchema>(it) }
            ?: NAppStateSchema()
        val ui = json.decodeFromString<NAppUISchema>(uiJson)
        val actions = actionsJson?.let { json.decodeFromString<NAppActionsSchema>(it) }
            ?: NAppActionsSchema()

        validate(state, ui, actions).let { errors ->
            if (errors.isNotEmpty()) {
                throw NAppValidationException(errors)
            }
        }

        NApp(manifest, state, ui, actions)
    }

    fun parseSingleJson(jsonString: String): Result<NApp> = runCatching {
        val root = json.parseToJsonElement(jsonString).jsonObject
        val manifestJson = root["manifest"]?.toString()
        val stateJson = root["state"]?.toString()
        val uiJson = root["ui"]?.toString()
            ?: throw IllegalArgumentException("Single-file format requires a 'ui' key")
        val actionsJson = root["actions"]?.toString()

        parse(manifestJson, stateJson, uiJson, actionsJson).getOrThrow()
    }

    private fun validate(
        state: NAppStateSchema,
        ui: NAppUISchema,
        actions: NAppActionsSchema
    ): List<String> {
        val errors = mutableListOf<String>()

        // Validate state field types
        state.schema.forEach { (key, field) ->
            if (field.type !in StateField.VALID_TYPES) {
                errors += "State field '$key' has invalid type '${field.type}'. Valid: ${StateField.VALID_TYPES}"
            }
        }

        // Validate component types and ID uniqueness
        val componentIds = mutableSetOf<String>()
        ui.components.forEach { comp ->
            if (!componentIds.add(comp.id)) {
                errors += "Duplicate component ID: '${comp.id}'"
            }
            if (comp.type !in NAppComponent.VALID_TYPES) {
                errors += "Component '${comp.id}' has invalid type '${comp.type}'. Valid: ${NAppComponent.VALID_TYPES}"
            }
        }

        // Validate children references
        ui.components.forEach { comp ->
            comp.children?.forEach { childId ->
                if (childId !in componentIds) {
                    errors += "Component '${comp.id}' references unknown child '$childId'"
                }
            }
        }

        // Validate action types
        actions.actions.forEach { (id, action) ->
            if (action.type !in NAppAction.VALID_TYPES) {
                errors += "Action '$id' has invalid type '${action.type}'. Valid: ${NAppAction.VALID_TYPES}"
            }
        }

        // Validate action references from components
        ui.components.forEach { comp ->
            comp.actionId?.let { actionId ->
                if (actionId !in actions.actions) {
                    errors += "Component '${comp.id}' references unknown action '$actionId'"
                }
            }
        }

        // Validate sub-action references
        actions.actions.forEach { (id, action) ->
            action.actions?.forEach { subId ->
                if (subId !in actions.actions) {
                    errors += "Action '$id' references unknown sub-action '$subId'"
                }
            }
            action.thenAction?.let { thenId ->
                if (thenId !in actions.actions) {
                    errors += "Action '$id' references unknown then-action '$thenId'"
                }
            }
            action.elseAction?.let { elseId ->
                if (elseId !in actions.actions) {
                    errors += "Action '$id' references unknown else-action '$elseId'"
                }
            }
        }

        return errors
    }
}

class NAppValidationException(val errors: List<String>) :
    Exception("NApp validation failed:\n${errors.joinToString("\n") { "  - $it" }}")

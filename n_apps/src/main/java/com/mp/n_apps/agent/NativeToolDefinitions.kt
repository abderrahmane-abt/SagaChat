package com.mp.n_apps.agent

import org.json.JSONArray
import org.json.JSONObject

object NativeToolDefinitions {

    fun buildToolsArray(): JSONArray {
        return JSONArray().apply {
            // File tools
            put(tool("read_file",
                "Read a project file (state.json, ui.json, actions.json, or manifest.json).",
                params("file" to stringEnum("state.json", "ui.json", "actions.json", "manifest.json")),
                required = listOf("file")
            ))

            put(tool("write_file",
                "Overwrite a project file entirely. content must be the complete JSON object with correct top-level wrapper key: ui.json needs {\"components\":[...]}, state.json needs {\"schema\":{...}}, actions.json needs {\"actions\":{...}}.",
                params(
                    "file" to stringEnum("state.json", "ui.json", "actions.json", "manifest.json"),
                    "content" to obj("The complete JSON content to write, must include correct wrapper key")
                ),
                required = listOf("file", "content")
            ))

            // Component tools
            put(tool("list_components",
                "List all component IDs and types in ui.json.",
                params(),
                required = emptyList()
            ))

            put(tool("add_component",
                "Add a UI component to ui.json. Must have unique id and type.",
                params("component" to obj("Component object with id, type, and other fields")),
                required = listOf("component")
            ))

            put(tool("update_component",
                "Merge fields into an existing component in ui.json.",
                params(
                    "id" to str("Component ID to update"),
                    "updates" to obj("Fields to merge into the component")
                ),
                required = listOf("id", "updates")
            ))

            put(tool("remove_component",
                "Remove a component from ui.json by ID.",
                params("id" to str("Component ID to remove")),
                required = listOf("id")
            ))

            // Action tools
            put(tool("add_action",
                "Add an action to actions.json.",
                params(
                    "id" to str("Action ID"),
                    "action" to obj("Action object with type and type-specific fields")
                ),
                required = listOf("id", "action")
            ))

            put(tool("update_action",
                "Merge fields into an existing action in actions.json.",
                params(
                    "id" to str("Action ID to update"),
                    "updates" to obj("Fields to merge into the action")
                ),
                required = listOf("id", "updates")
            ))

            put(tool("remove_action",
                "Remove an action from actions.json by ID.",
                params("id" to str("Action ID to remove")),
                required = listOf("id")
            ))

            // State tools
            put(tool("set_state_field",
                "Set or update a field in state.json schema.",
                params(
                    "key" to str("State field name"),
                    "field" to obj("Field definition, e.g. {\"type\":\"number\",\"default\":0}")
                ),
                required = listOf("key", "field")
            ))

            put(tool("remove_state_field",
                "Remove a field from state.json schema.",
                params("key" to str("State field name to remove")),
                required = listOf("key")
            ))

            // VCS tools
            put(tool("commit",
                "Save a version snapshot with a description.",
                params("message" to str("Description of changes")),
                required = listOf("message")
            ))

            put(tool("list_versions",
                "List version history.",
                params(),
                required = emptyList()
            ))

            put(tool("revert",
                "Revert to a previous version number.",
                params("version" to int("Version number to revert to")),
                required = listOf("version")
            ))

            // Info tools
            put(tool("get_schema_help",
                "Get NApp schema documentation about components, actions, state, expressions, or layout.",
                params("topic" to stringEnum("components", "actions", "state", "expressions", "layout")),
                required = emptyList()
            ))
        }
    }

    // ── Schema builders ──

    private fun tool(
        name: String,
        description: String,
        properties: JSONObject,
        required: List<String>
    ): JSONObject = JSONObject().apply {
        put("type", "function")
        put("function", JSONObject().apply {
            put("name", name)
            put("description", description)
            put("parameters", JSONObject().apply {
                put("type", "object")
                put("properties", properties)
                put("required", JSONArray(required))
                put("additionalProperties", false)
            })
        })
    }

    private fun params(vararg pairs: Pair<String, JSONObject>): JSONObject =
        JSONObject().apply { pairs.forEach { put(it.first, it.second) } }

    private fun str(desc: String): JSONObject = JSONObject().apply {
        put("type", "string")
        put("description", desc)
    }

    private fun int(desc: String): JSONObject = JSONObject().apply {
        put("type", "integer")
        put("description", desc)
    }

    private fun obj(desc: String): JSONObject = JSONObject().apply {
        put("type", "object")
        put("description", desc)
    }

    private fun stringEnum(vararg values: String): JSONObject = JSONObject().apply {
        put("type", "string")
        put("enum", JSONArray(values.toList()))
    }
}

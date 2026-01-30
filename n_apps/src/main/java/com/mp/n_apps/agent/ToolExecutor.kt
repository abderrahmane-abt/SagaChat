package com.mp.n_apps.agent

import android.util.Log
import com.mp.n_apps.vcs.NAppVersionControl
import com.mp.n_apps.workspace.ProjectFiles
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class ToolExecutor(
    private val getCurrentFiles: () -> ProjectFiles,
    private val updateFiles: (ProjectFiles) -> Unit,
    private val vcs: NAppVersionControl?
) {
    companion object {
        private const val TAG = "ToolExecutor"
    }

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; isLenient = true }

    fun execute(toolCall: ToolCall): ToolResult {
        return try {
            when (toolCall.name) {
                // File tools
                "read_file" -> executeReadFile(toolCall.params)
                "write_file" -> executeWriteFile(toolCall.params)

                // Component tools
                "list_components" -> executeListComponents()
                "add_component" -> executeAddComponent(toolCall.params)
                "update_component" -> executeUpdateComponent(toolCall.params)
                "remove_component" -> executeRemoveComponent(toolCall.params)

                // Action tools
                "add_action" -> executeAddAction(toolCall.params)
                "update_action" -> executeUpdateAction(toolCall.params)
                "remove_action" -> executeRemoveAction(toolCall.params)

                // State tools
                "set_state_field" -> executeSetStateField(toolCall.params)
                "remove_state_field" -> executeRemoveStateField(toolCall.params)

                // VCS tools
                "commit" -> executeCommit(toolCall.params)
                "list_versions" -> executeListVersions()
                "revert" -> executeRevert(toolCall.params)

                // Info tools
                "get_schema_help" -> executeGetSchemaHelp(toolCall.params)

                else -> ToolResult(toolCall.name, false, error = "Unknown tool: ${toolCall.name}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Tool execution failed: ${toolCall.name}", e)
            ToolResult(toolCall.name, false, error = e.message ?: "Execution failed")
        }
    }

    // ═══════════════════════════════
    //  File Tools
    // ═══════════════════════════════

    private fun executeReadFile(params: JsonObject): ToolResult {
        val fileName = params.str("file") ?: return ToolResult("read_file", false, error = "Missing 'file' param")
        val files = getCurrentFiles()
        val content = when (fileName) {
            "state.json" -> files.stateJson
            "ui.json" -> files.uiJson
            "actions.json" -> files.actionsJson
            "manifest.json" -> files.manifestJson
            else -> return ToolResult("read_file", false, error = "Unknown file: $fileName")
        }
        return ToolResult("read_file", true, data = JsonPrimitive(content))
    }

    private fun executeWriteFile(params: JsonObject): ToolResult {
        val fileName = params.str("file") ?: return ToolResult("write_file", false, error = "Missing 'file' param")
        val content = params["content"] ?: return ToolResult("write_file", false, error = "Missing 'content' param")
        val contentStr = if (content is JsonPrimitive && content.isString) {
            content.content
        } else {
            json.encodeToString(JsonElement.serializer(), content)
        }

        // Validate file structure before writing
        val structureError = validateFileStructure(fileName, contentStr)
        if (structureError != null) {
            return ToolResult("write_file", false, error = structureError)
        }

        val files = getCurrentFiles()
        val updated = when (fileName) {
            "state.json" -> files.copy(stateJson = contentStr)
            "ui.json" -> files.copy(uiJson = contentStr)
            "actions.json" -> files.copy(actionsJson = contentStr)
            "manifest.json" -> files.copy(manifestJson = contentStr)
            else -> return ToolResult("write_file", false, error = "Unknown file: $fileName")
        }
        updateFiles(updated)

        // After writing, validate the full app and report warnings
        val warnings = validateAfterWrite(updated, fileName)
        val message = if (warnings.isEmpty()) {
            "$fileName updated successfully"
        } else {
            "$fileName updated with ${warnings.size} validation warning(s):\n${warnings.joinToString("\n") { "- $it" }}\nPlease fix these issues."
        }

        return ToolResult("write_file", true, data = JsonPrimitive(message))
    }

    /**
     * Validate that the JSON structure matches expected format for each file.
     * Returns error string if invalid, null if ok.
     */
    private fun validateFileStructure(fileName: String, contentStr: String): String? {
        return try {
            val parsed = json.parseToJsonElement(contentStr)
            when (fileName) {
                "ui.json" -> {
                    if (parsed !is JsonObject) {
                        return "ui.json must be a JSON object like {\"components\": [...]}, not ${parsed::class.simpleName}. Got: ${contentStr.take(50)}"
                    }
                    if (!parsed.containsKey("components")) {
                        return "ui.json must have a \"components\" array. Expected: {\"components\": [...]}"
                    }
                    if (parsed["components"] !is JsonArray) {
                        return "ui.json \"components\" must be an array, not ${parsed["components"]!!::class.simpleName}"
                    }
                    null
                }
                "state.json" -> {
                    if (parsed !is JsonObject) {
                        return "state.json must be a JSON object like {\"schema\": {...}}, not ${parsed::class.simpleName}"
                    }
                    if (!parsed.containsKey("schema")) {
                        return "state.json must have a \"schema\" object. Expected: {\"schema\": {...}}"
                    }
                    null
                }
                "actions.json" -> {
                    if (parsed !is JsonObject) {
                        return "actions.json must be a JSON object like {\"actions\": {...}}, not ${parsed::class.simpleName}"
                    }
                    if (!parsed.containsKey("actions")) {
                        return "actions.json must have an \"actions\" object. Expected: {\"actions\": {...}}"
                    }
                    null
                }
                else -> null
            }
        } catch (e: Exception) {
            "Invalid JSON: ${e.message}"
        }
    }

    /**
     * After a write, run NAppParser validation and return warnings.
     * The write still succeeds (data is saved), but warnings tell the AI what to fix.
     */
    private fun validateAfterWrite(files: ProjectFiles, changedFile: String): List<String> {
        return try {
            val result = com.mp.n_apps.schema.NAppParser.parse(
                stateJson = files.stateJson.ifBlank { null },
                uiJson = files.uiJson,
                actionsJson = files.actionsJson.ifBlank { null }
            )
            result.fold(
                onSuccess = { emptyList() },
                onFailure = { error ->
                    if (error is com.mp.n_apps.schema.NAppValidationException) {
                        error.errors
                    } else {
                        listOf(error.message ?: "Parse error after writing $changedFile")
                    }
                }
            )
        } catch (e: Exception) {
            listOf("Parse error after writing $changedFile: ${e.message}")
        }
    }

    // ═══════════════════════════════
    //  Component Tools
    // ═══════════════════════════════

    private fun executeListComponents(): ToolResult {
        val files = getCurrentFiles()
        val uiObj = json.parseToJsonElement(files.uiJson).jsonObject
        val components = uiObj["components"]?.jsonArray ?: JsonArray(emptyList())
        val summary = components.map { comp ->
            val obj = comp.jsonObject
            val id = obj.str("id") ?: "?"
            val type = obj.str("type") ?: "?"
            "$id ($type)"
        }
        return ToolResult("list_components", true, data = JsonPrimitive(summary.joinToString(", ")))
    }

    private fun executeAddComponent(params: JsonObject): ToolResult {
        val component = params["component"]?.jsonObject
            ?: return ToolResult("add_component", false, error = "Missing 'component' param")
        val id = component.str("id")
            ?: return ToolResult("add_component", false, error = "Component must have 'id'")

        val files = getCurrentFiles()
        val uiObj = json.parseToJsonElement(files.uiJson).jsonObject
        val components = uiObj["components"]?.jsonArray?.toMutableList() ?: mutableListOf()

        // Check duplicate
        if (components.any { it.jsonObject.str("id") == id }) {
            return ToolResult("add_component", false, error = "Component '$id' already exists")
        }

        components.add(component)
        val newUi = buildJsonObject {
            uiObj.forEach { (key, value) ->
                if (key != "components") put(key, value)
            }
            put("components", JsonArray(components))
        }

        updateFiles(files.copy(uiJson = json.encodeToString(JsonElement.serializer(), newUi)))
        return ToolResult("add_component", true, data = JsonPrimitive("Component '$id' added"))
    }

    private fun executeUpdateComponent(params: JsonObject): ToolResult {
        val id = params.str("id")
            ?: return ToolResult("update_component", false, error = "Missing 'id' param")
        val updates = params["updates"]?.jsonObject
            ?: return ToolResult("update_component", false, error = "Missing 'updates' param")

        val files = getCurrentFiles()
        val uiObj = json.parseToJsonElement(files.uiJson).jsonObject
        val components = uiObj["components"]?.jsonArray?.toMutableList() ?: mutableListOf()

        val index = components.indexOfFirst { it.jsonObject.str("id") == id }
        if (index == -1) {
            return ToolResult("update_component", false, error = "Component '$id' not found")
        }

        val existing = components[index].jsonObject
        val merged = buildJsonObject {
            existing.forEach { (key, value) -> put(key, value) }
            updates.forEach { (key, value) -> put(key, value) }
        }
        components[index] = merged

        val newUi = buildJsonObject {
            uiObj.forEach { (key, value) ->
                if (key != "components") put(key, value)
            }
            put("components", JsonArray(components))
        }

        updateFiles(files.copy(uiJson = json.encodeToString(JsonElement.serializer(), newUi)))
        return ToolResult("update_component", true, data = JsonPrimitive("Component '$id' updated"))
    }

    private fun executeRemoveComponent(params: JsonObject): ToolResult {
        val id = params.str("id")
            ?: return ToolResult("remove_component", false, error = "Missing 'id' param")

        val files = getCurrentFiles()
        val uiObj = json.parseToJsonElement(files.uiJson).jsonObject
        val components = uiObj["components"]?.jsonArray?.toMutableList() ?: mutableListOf()

        val removed = components.removeAll { it.jsonObject.str("id") == id }
        if (!removed) {
            return ToolResult("remove_component", false, error = "Component '$id' not found")
        }

        val newUi = buildJsonObject {
            uiObj.forEach { (key, value) ->
                if (key != "components") put(key, value)
            }
            put("components", JsonArray(components))
        }

        updateFiles(files.copy(uiJson = json.encodeToString(JsonElement.serializer(), newUi)))
        return ToolResult("remove_component", true, data = JsonPrimitive("Component '$id' removed"))
    }

    // ═══════════════════════════════
    //  Action Tools
    // ═══════════════════════════════

    private fun executeAddAction(params: JsonObject): ToolResult {
        val id = params.str("id")
            ?: return ToolResult("add_action", false, error = "Missing 'id' param")
        val action = params["action"]?.jsonObject
            ?: return ToolResult("add_action", false, error = "Missing 'action' param")

        val files = getCurrentFiles()
        val actionsObj = json.parseToJsonElement(files.actionsJson).jsonObject
        val actionsMap = actionsObj["actions"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        if (actionsMap.containsKey(id)) {
            return ToolResult("add_action", false, error = "Action '$id' already exists")
        }

        actionsMap[id] = action
        val newActions = buildJsonObject {
            actionsObj.forEach { (key, value) ->
                if (key != "actions") put(key, value)
            }
            put("actions", JsonObject(actionsMap))
        }

        updateFiles(files.copy(actionsJson = json.encodeToString(JsonElement.serializer(), newActions)))
        return ToolResult("add_action", true, data = JsonPrimitive("Action '$id' added"))
    }

    private fun executeUpdateAction(params: JsonObject): ToolResult {
        val id = params.str("id")
            ?: return ToolResult("update_action", false, error = "Missing 'id' param")
        val updates = params["updates"]?.jsonObject
            ?: return ToolResult("update_action", false, error = "Missing 'updates' param")

        val files = getCurrentFiles()
        val actionsObj = json.parseToJsonElement(files.actionsJson).jsonObject
        val actionsMap = actionsObj["actions"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        val existing = actionsMap[id]?.jsonObject
            ?: return ToolResult("update_action", false, error = "Action '$id' not found")

        val merged = buildJsonObject {
            existing.forEach { (key, value) -> put(key, value) }
            updates.forEach { (key, value) -> put(key, value) }
        }
        actionsMap[id] = merged

        val newActions = buildJsonObject {
            actionsObj.forEach { (key, value) ->
                if (key != "actions") put(key, value)
            }
            put("actions", JsonObject(actionsMap))
        }

        updateFiles(files.copy(actionsJson = json.encodeToString(JsonElement.serializer(), newActions)))
        return ToolResult("update_action", true, data = JsonPrimitive("Action '$id' updated"))
    }

    private fun executeRemoveAction(params: JsonObject): ToolResult {
        val id = params.str("id")
            ?: return ToolResult("remove_action", false, error = "Missing 'id' param")

        val files = getCurrentFiles()
        val actionsObj = json.parseToJsonElement(files.actionsJson).jsonObject
        val actionsMap = actionsObj["actions"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        if (!actionsMap.containsKey(id)) {
            return ToolResult("remove_action", false, error = "Action '$id' not found")
        }
        actionsMap.remove(id)

        val newActions = buildJsonObject {
            actionsObj.forEach { (key, value) ->
                if (key != "actions") put(key, value)
            }
            put("actions", JsonObject(actionsMap))
        }

        updateFiles(files.copy(actionsJson = json.encodeToString(JsonElement.serializer(), newActions)))
        return ToolResult("remove_action", true, data = JsonPrimitive("Action '$id' removed"))
    }

    // ═══════════════════════════════
    //  State Tools
    // ═══════════════════════════════

    private fun executeSetStateField(params: JsonObject): ToolResult {
        val key = params.str("key")
            ?: return ToolResult("set_state_field", false, error = "Missing 'key' param")
        val field = params["field"]?.jsonObject
            ?: return ToolResult("set_state_field", false, error = "Missing 'field' param")

        val files = getCurrentFiles()
        val stateObj = json.parseToJsonElement(files.stateJson).jsonObject
        val schema = stateObj["schema"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        schema[key] = field
        val newState = buildJsonObject {
            stateObj.forEach { (k, v) ->
                if (k != "schema") put(k, v)
            }
            put("schema", JsonObject(schema))
        }

        updateFiles(files.copy(stateJson = json.encodeToString(JsonElement.serializer(), newState)))
        return ToolResult("set_state_field", true, data = JsonPrimitive("State field '$key' set"))
    }

    private fun executeRemoveStateField(params: JsonObject): ToolResult {
        val key = params.str("key")
            ?: return ToolResult("remove_state_field", false, error = "Missing 'key' param")

        val files = getCurrentFiles()
        val stateObj = json.parseToJsonElement(files.stateJson).jsonObject
        val schema = stateObj["schema"]?.jsonObject?.toMutableMap() ?: mutableMapOf()

        if (!schema.containsKey(key)) {
            return ToolResult("remove_state_field", false, error = "State field '$key' not found")
        }
        schema.remove(key)

        val newState = buildJsonObject {
            stateObj.forEach { (k, v) ->
                if (k != "schema") put(k, v)
            }
            put("schema", JsonObject(schema))
        }

        updateFiles(files.copy(stateJson = json.encodeToString(JsonElement.serializer(), newState)))
        return ToolResult("remove_state_field", true, data = JsonPrimitive("State field '$key' removed"))
    }

    // ═══════════════════════════════
    //  VCS Tools
    // ═══════════════════════════════

    private fun executeCommit(params: JsonObject): ToolResult {
        val vcsInstance = vcs ?: return ToolResult("commit", false, error = "VCS not available (no project open)")
        val message = params.str("message") ?: "Auto-commit"
        val entry = vcsInstance.commit(getCurrentFiles(), message)
        return ToolResult("commit", true, data = JsonPrimitive("Version ${entry.versionNumber} committed: $message"))
    }

    private fun executeListVersions(): ToolResult {
        val vcsInstance = vcs ?: return ToolResult("list_versions", false, error = "VCS not available")
        val history = vcsInstance.listHistory()
        if (history.isEmpty()) {
            return ToolResult("list_versions", true, data = JsonPrimitive("No versions yet"))
        }
        val summary = history.joinToString("\n") { "v${it.versionNumber}: ${it.message}" }
        return ToolResult("list_versions", true, data = JsonPrimitive(summary))
    }

    private fun executeRevert(params: JsonObject): ToolResult {
        val vcsInstance = vcs ?: return ToolResult("revert", false, error = "VCS not available")
        val version = params["version"]?.jsonPrimitive?.content?.toIntOrNull()
            ?: return ToolResult("revert", false, error = "Missing or invalid 'version' param")
        val files = vcsInstance.revert(version)
            ?: return ToolResult("revert", false, error = "Version $version not found")
        updateFiles(files)
        return ToolResult("revert", true, data = JsonPrimitive("Reverted to version $version"))
    }

    // ═══════════════════════════════
    //  Info Tools
    // ═══════════════════════════════

    private fun executeGetSchemaHelp(params: JsonObject): ToolResult {
        val topic = params.str("topic")
        val help = SchemaDocumentation.getHelp(topic)
        return ToolResult("get_schema_help", true, data = JsonPrimitive(help))
    }

    // ═══════════════════════════════
    //  Helpers
    // ═══════════════════════════════

    private fun JsonObject.str(key: String): String? =
        this[key]?.jsonPrimitive?.content
}

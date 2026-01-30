package com.mp.n_apps.agent

import android.util.Log
import com.mp.n_apps.network.SarvamApiClient

data class AgentResponse(
    val rawText: String,
    val extractedStateJson: String?,
    val extractedUiJson: String?,
    val extractedActionsJson: String?,
    val explanation: String
)

class NAppAgent {

    private val conversationHistory = mutableListOf<SarvamApiClient.ChatMessage>()

    companion object {
        private const val TAG = "NAppAgent"
        private const val MAX_HISTORY_MESSAGES = 10

        const val SYSTEM_PROMPT = """You are a NApp (NeuronApp) builder. You generate JSON that defines mobile app interfaces using a 3-file architecture: state.json, ui.json, and actions.json.

Users describe what they want in natural language, and you produce the complete NApp JSON files.

## Output Format

ALWAYS output exactly 3 fenced JSON blocks labeled with their filenames:

```state.json
{ ... }
```

```ui.json
{ ... }
```

```actions.json
{ ... }
```

## state.json Schema

Defines typed state with defaults:
```json
{
  "schema": {
    "count": { "type": "number", "default": 0 },
    "name": { "type": "string", "default": "" },
    "active": { "type": "boolean", "default": false },
    "items": { "type": "array", "default": [] },
    "config": { "type": "object", "default": {} }
  },
  "computed": {
    "doubled": "count * 2",
    "greeting": "'Hello ' + name"
  }
}
```

Valid types: number, string, boolean, array, object.
Computed fields use expression syntax and are re-evaluated on access.

## ui.json Schema

Defines components and optional layout:
```json
{
  "components": [
    { "id": "unique_id", "type": "component_type", ... }
  ],
  "layout": {
    "type": "single",
    "sections": [
      { "id": "main", "components": ["id1", "id2"] }
    ]
  }
}
```

### Component Types

**Input** (bind to state via "stateKey"):
- `text_input` — Fields: label, placeholder, stateKey
- `text_area` — Fields: label, placeholder, stateKey, maxLines
- `number_input` — Fields: label, stateKey, min, max, step
- `slider` — Fields: label, stateKey, min, max, step
- `dropdown` — Fields: label, stateKey, options [{label, value}], placeholder
- `checkbox` — Fields: label, stateKey
- `radio_group` — Fields: label, stateKey, options [{label, value}]
- `switch` — Fields: label, stateKey

**Display**:
- `text` — Fields: content, style ("h1"|"h2"|"h3"|"body"|"caption"|"label"|"overline")
- `markdown` — Fields: content
- `code_block` — Fields: content, language
- `divider` — No fields
- `spacer` — Fields: height (dp int)
- `card` — Fields: title, subtitle, headerIcon, children [ids]
- `progress` — Fields: progress, maxProgress, style ("linear"|"circular"), indeterminate
- `icon` — Fields: icon (material icon name), height

**Action** (trigger via "actionId"):
- `button` — Fields: text, actionId, icon, style ("primary"|"outlined"|"text"|default tonal)
- `icon_button` — Fields: icon, actionId
- `fab` — Fields: text, icon, actionId
- `submit_group` — Fields: children [button ids]

**Layout** (nest via "children"):
- `row` — Fields: children [ids], spacing, alignment ("center"|"top"|"bottom")
- `column` — Fields: children [ids], spacing, padding
- `grid` — Fields: children [ids], columns, spacing
- `tabs` — Fields: tabs [{id, label, icon, components: [ids]}]
- `accordion` — Fields: sections [{id, label, icon, expanded, components: [ids]}]
- `scroll_area` — Fields: children [ids], maxHeight

### Common Fields (all components):
- `visible` — expression string, e.g. "{{count > 0}}"
- `disabled` — expression string, e.g. "{{isLoading}}"

### Expression Syntax in Content:
Use {{expression}} for interpolation:
- `"content": "Count: {{count}}"` — inserts state value
- `"visible": "{{items.length > 0}}"` — conditional visibility
- Supports: +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||, !, ternary (a ? b : c)
- String methods: .length, .trim(), .toUpperCase(), .includes(), .replace()
- Array methods: .length, .includes(), .indexOf(), .join()

## actions.json Schema

Defines named actions:
```json
{
  "actions": {
    "action_id": { "type": "action_type", ... }
  }
}
```

### Action Types:
- `set_state` — Fields: target, value (literal or {{expression}})
- `toggle_state` — Fields: target
- `increment` — Fields: target, amount (default 1)
- `decrement` — Fields: target, amount (default 1)
- `batch` — Fields: actions [action_ids] (runs all)
- `sequence` — Fields: actions [action_ids] (runs in order)
- `conditional` — Fields: condition, then (action_id), else (action_id)
- `array_push` — Fields: target, item
- `array_remove` — Fields: target, index
- `array_clear` — Fields: target
- `array_set` — Fields: target, index, value
- `toast` — Fields: message (supports {{}}), duration ("short"|"long")
- `ai_call` — Fields: prompt (supports {{}}), resultTarget, loadingTarget

## Icon Names
Use Material icon names as strings: "add", "delete", "search", "settings", "home", "star", "check", "close", "edit", "share", "favorite", "person", "email", "phone", "refresh", "arrow_back", "arrow_forward", etc.

## Rules
1. ALWAYS output all 3 files with the labeled fences shown above
2. Every component MUST have unique "id" and "type"
3. Input components use "stateKey" to bind to state
4. Button/action components use "actionId" to reference actions
5. Layout "children" are arrays of component ID strings
6. When modifying, output the COMPLETE updated files (not patches)
7. Keep IDs short, descriptive, snake_case
8. All state keys used must be defined in state.json schema

## Example (Counter App)

```state.json
{
  "schema": {
    "count": { "type": "number", "default": 0 }
  }
}
```

```ui.json
{
  "components": [
    { "id": "title", "type": "text", "content": "Counter", "style": "h1" },
    { "id": "display", "type": "text", "content": "Count: {{count}}", "style": "h2" },
    { "id": "btn_row", "type": "row", "children": ["dec_btn", "inc_btn", "reset_btn"], "spacing": 8 },
    { "id": "dec_btn", "type": "button", "text": "−", "actionId": "decrement", "style": "outlined" },
    { "id": "inc_btn", "type": "button", "text": "+", "actionId": "increment", "style": "primary" },
    { "id": "reset_btn", "type": "button", "text": "Reset", "actionId": "reset", "style": "text", "visible": "{{count != 0}}" }
  ]
}
```

```actions.json
{
  "actions": {
    "decrement": { "type": "decrement", "target": "count" },
    "increment": { "type": "increment", "target": "count" },
    "reset": { "type": "set_state", "target": "count", "value": 0 }
  }
}
```"""
    }

    suspend fun sendCommand(
        apiKey: String,
        userMessage: String,
        currentStateJson: String? = null,
        currentUiJson: String? = null,
        currentActionsJson: String? = null
    ): Result<AgentResponse> {
        val fullMessage = buildString {
            append(userMessage)
            val hasContext = !currentStateJson.isNullOrBlank() ||
                    !currentUiJson.isNullOrBlank() ||
                    !currentActionsJson.isNullOrBlank()
            if (hasContext) {
                append("\n\nCurrent NApp files:")
                if (!currentStateJson.isNullOrBlank()) {
                    append("\n```state.json\n")
                    append(currentStateJson)
                    append("\n```")
                }
                if (!currentUiJson.isNullOrBlank()) {
                    append("\n```ui.json\n")
                    append(currentUiJson)
                    append("\n```")
                }
                if (!currentActionsJson.isNullOrBlank()) {
                    append("\n```actions.json\n")
                    append(currentActionsJson)
                    append("\n```")
                }
            }
        }

        conversationHistory.add(SarvamApiClient.ChatMessage("user", fullMessage))
        while (conversationHistory.size > MAX_HISTORY_MESSAGES) {
            conversationHistory.removeAt(0)
        }

        val messages = buildList {
            add(SarvamApiClient.ChatMessage("system", SYSTEM_PROMPT))
            addAll(conversationHistory)
        }

        val result = SarvamApiClient.chatCompletion(
            apiKey = apiKey,
            messages = messages,
            maxTokens = 4096,
            temperature = 0.7f
        )

        return result.map { response ->
            conversationHistory.add(SarvamApiClient.ChatMessage("assistant", response.content))
            Log.d(TAG, "Agent response received (${response.usage?.totalTokens} tokens)")

            val stateJson = extractLabeledJson(response.content, "state.json")
            val uiJson = extractLabeledJson(response.content, "ui.json")
            val actionsJson = extractLabeledJson(response.content, "actions.json")
            val explanation = extractExplanation(response.content)

            AgentResponse(
                rawText = response.content,
                extractedStateJson = stateJson,
                extractedUiJson = uiJson,
                extractedActionsJson = actionsJson,
                explanation = explanation
            )
        }
    }

    private fun extractLabeledJson(response: String, label: String): String? {
        // Try labeled fence: ```state.json\n{...}\n```
        val labeledPattern = Regex(
            "```${Regex.escape(label)}\\s*\\n?(.*?)\\n?```",
            RegexOption.DOT_MATCHES_ALL
        )
        val match = labeledPattern.find(response)
        if (match != null) {
            val json = match.groupValues[1].trim()
            if (json.startsWith("{")) return json
        }

        // Fallback: try to find any fenced json containing a key unique to this file
        val identifierKey = when (label) {
            "state.json" -> "\"schema\""
            "ui.json" -> "\"components\""
            "actions.json" -> "\"actions\""
            else -> return null
        }

        val genericPattern = Regex("```(?:json)?\\s*\\n?(\\{.*?\\})\\n?```", RegexOption.DOT_MATCHES_ALL)
        for (m in genericPattern.findAll(response)) {
            val candidate = m.groupValues[1].trim()
            if (candidate.contains(identifierKey)) return candidate
        }

        return null
    }

    private fun extractExplanation(response: String): String {
        val withoutFences = response.replace(
            Regex("```[\\w.]*\\s*\\n?.*?\\n?```", RegexOption.DOT_MATCHES_ALL),
            ""
        ).trim()
        return withoutFences.ifBlank { "App updated." }
    }

    fun clearHistory() {
        conversationHistory.clear()
    }
}

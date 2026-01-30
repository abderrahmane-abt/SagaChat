package com.mp.n_apps.agent

object SchemaDocumentation {

    fun getHelp(topic: String? = null): String = when (topic?.lowercase()) {
        "components", "component" -> COMPONENTS_HELP
        "actions", "action" -> ACTIONS_HELP
        "state" -> STATE_HELP
        "expressions", "expression" -> EXPRESSIONS_HELP
        "layout" -> LAYOUT_HELP
        else -> OVERVIEW_HELP
    }

    private const val OVERVIEW_HELP = """NApp Schema Overview:
A NApp is defined by 3 JSON files:
- state.json: Typed state fields with defaults and computed values
- ui.json: Component tree (input, display, action, layout types)
- actions.json: Named actions (set_state, toggle, increment, batch, conditional, etc.)

Topics: components, actions, state, expressions, layout"""

    private const val COMPONENTS_HELP = """Component Types:

INPUT (bind via "stateKey"):
- text_input: label, placeholder, stateKey
- text_area: label, placeholder, stateKey, maxLines
- number_input: label, stateKey, min, max, step
- slider: label, stateKey, min, max, step
- dropdown: label, stateKey, options [{label, value}], placeholder
- checkbox: label, stateKey
- radio_group: label, stateKey, options [{label, value}]
- switch: label, stateKey

DISPLAY:
- text: content, style (h1|h2|h3|body|caption|label|overline)
- markdown: content
- code_block: content, language
- divider: (no fields)
- spacer: height
- card: title, subtitle, headerIcon, children [ids]
- progress: progress, maxProgress, style (linear|circular), indeterminate
- icon: icon, height

ACTION (trigger via "actionId"):
- button: text, actionId, icon, style (primary|outlined|text|tonal)
- icon_button: icon, actionId
- fab: text, icon, actionId
- submit_group: children [button ids]

LAYOUT (nest via "children"):
- row: children [ids], spacing, alignment (center|top|bottom)
- column: children [ids], spacing, padding
- grid: children [ids], columns, spacing
- tabs: tabs [{id, label, icon, components: [ids]}]
- accordion: sections [{id, label, icon, expanded, components: [ids]}]
- scroll_area: children [ids], maxHeight

Common fields: visible (expression), disabled (expression)"""

    private const val ACTIONS_HELP = """Action Types:

STATE:
- set_state: target, value (literal or {{expression}})
- toggle_state: target
- increment: target, amount (default 1)
- decrement: target, amount (default 1)

CONTROL:
- batch: actions [action_ids] (runs all)
- sequence: actions [action_ids] (runs in order)
- conditional: condition, then (action_id), else (action_id)

ARRAY:
- array_push: target, item
- array_remove: target, index
- array_clear: target
- array_set: target, index, value

SYSTEM:
- toast: message (supports {{}}), duration (short|long)

AI:
- ai_call: prompt (supports {{}}), resultTarget, loadingTarget"""

    private const val STATE_HELP = """State Schema:

state.json defines typed fields with defaults:
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

Valid types: number, string, boolean, array, object
Computed fields are re-evaluated on access."""

    private const val EXPRESSIONS_HELP = """Expression Syntax:

Use {{expression}} in content strings:
- "Count: {{count}}" - inserts state value
- "{{items.length > 0}}" - conditional

Operators: +, -, *, /, %, ==, !=, <, >, <=, >=, &&, ||, !
Ternary: a ? b : c
String methods: .length, .trim(), .toUpperCase(), .includes(), .replace()
Array methods: .length, .includes(), .indexOf(), .join()"""

    private const val LAYOUT_HELP = """Layout System:

ui.json can include an optional layout object:
{
  "layout": {
    "type": "single",
    "sections": [
      { "id": "main", "components": ["id1", "id2"] }
    ]
  }
}

Top-level components without a layout are rendered in order.
Use row/column/grid components for inline layout.
Use tabs/accordion for organized sections."""
}

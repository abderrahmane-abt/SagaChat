package com.mp.n_apps.schema

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class NAppUISchema(
    val components: List<NAppComponent> = emptyList(),
    val layout: NAppLayout? = null
)

@Serializable
data class NAppComponent(
    // Identity
    val id: String,
    val type: String,

    // Common
    val visible: String? = null,
    val disabled: String? = null,

    // Content / Display
    val content: String? = null,
    val text: String? = null,
    val label: String? = null,
    val placeholder: String? = null,
    val style: String? = null,
    val icon: String? = null,
    val language: String? = null,
    val progress: String? = null,
    val maxProgress: String? = null,
    val indeterminate: Boolean? = null,

    // Input
    val stateKey: String? = null,
    val inputType: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val options: List<ComponentOption>? = null,
    val maxLines: Int? = null,

    // Action
    val actionId: String? = null,

    // Layout
    val children: List<String>? = null,
    val spacing: Int? = null,
    val alignment: String? = null,
    val columns: Int? = null,
    val tabs: List<TabSpec>? = null,
    val sections: List<AccordionSection>? = null,
    val padding: Int? = null,
    val weight: Float? = null,

    // Card
    val title: String? = null,
    val subtitle: String? = null,
    val headerIcon: String? = null,

    // Size
    val height: Int? = null,
    val maxHeight: Int? = null
) {
    companion object {
        val VALID_TYPES = setOf(
            // Input
            "text_input", "text_area", "number_input", "slider",
            "dropdown", "checkbox", "radio_group", "switch",
            // Display
            "text", "markdown", "code_block", "divider", "spacer",
            "card", "progress", "icon",
            // Action
            "button", "icon_button", "fab", "submit_group",
            // Layout
            "row", "column", "grid", "tabs", "accordion", "scroll_area"
        )
    }
}

@Serializable
data class ComponentOption(
    val label: String,
    val value: String
)

@Serializable
data class NAppLayout(
    val type: String = "single",
    val sections: List<LayoutSection>? = null
)

@Serializable
data class LayoutSection(
    val id: String,
    val components: List<String> = emptyList(),
    val label: String? = null,
    val weight: Float? = null
)

@Serializable
data class TabSpec(
    val id: String,
    val label: String,
    val icon: String? = null,
    val components: List<String> = emptyList()
)

@Serializable
data class AccordionSection(
    val id: String,
    val label: String,
    val icon: String? = null,
    val expanded: Boolean = false,
    val components: List<String> = emptyList()
)

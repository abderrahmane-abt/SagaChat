package com.dark.userdata.ntds.schema

import kotlinx.serialization.Serializable

@Serializable
data class NodeContentSchema(
    val name: String = "",
    val content: String = "",
    val tags: List<NodeTags> = emptyList(),
    val childNodes: List<ChildNodeSchema> = emptyList(),
)

@Serializable
data class ChildNodeSchema(
    val childId: String = ""
)


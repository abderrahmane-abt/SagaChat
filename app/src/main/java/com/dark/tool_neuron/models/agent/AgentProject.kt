package com.dark.tool_neuron.models.agent

data class AgentProject(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val toolModelId: String? = null,
    val writerModelId: String = "",
    val createdAt: Long = 0L,
    val lastOpenedAt: Long = 0L,
    val messageCount: Int = 0,
    val status: ProjectStatus = ProjectStatus.ACTIVE
)

enum class ProjectStatus {
    ACTIVE,
    ARCHIVED;

    companion object {
        fun from(name: String): ProjectStatus =
            entries.firstOrNull { it.name == name } ?: ACTIVE
    }
}

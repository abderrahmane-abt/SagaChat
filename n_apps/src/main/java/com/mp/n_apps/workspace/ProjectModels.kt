package com.mp.n_apps.workspace

import kotlinx.serialization.Serializable

@Serializable
data class ProjectMetadata(
    val id: String,
    val name: String,
    val description: String = "",
    val createdAt: Long,
    val lastModifiedAt: Long,
    val version: String = "1.0.0"
)

@Serializable
data class ProjectIndex(val projects: List<ProjectMetadata> = emptyList())

data class ProjectFiles(
    val manifestJson: String,
    val stateJson: String,
    val uiJson: String,
    val actionsJson: String
)

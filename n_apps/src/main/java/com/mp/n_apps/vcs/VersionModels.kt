package com.mp.n_apps.vcs

import kotlinx.serialization.Serializable

@Serializable
data class VersionEntry(
    val versionNumber: Int,
    val timestamp: Long,
    val message: String
)

@Serializable
data class VersionHistory(
    val projectId: String,
    val versions: List<VersionEntry> = emptyList()
)

@Serializable
data class VersionSnapshot(
    val versionNumber: Int,
    val timestamp: Long,
    val message: String,
    val manifestJson: String,
    val stateJson: String,
    val uiJson: String,
    val actionsJson: String
)

@Serializable
data class VersionDiff(
    val fromVersion: Int,
    val toVersion: Int,
    val stateChanged: Boolean,
    val uiChanged: Boolean,
    val actionsChanged: Boolean,
    val manifestChanged: Boolean
)

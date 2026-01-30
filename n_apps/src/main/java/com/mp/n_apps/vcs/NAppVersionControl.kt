package com.mp.n_apps.vcs

import com.mp.n_apps.workspace.ProjectFiles
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class NAppVersionControl(private val projectDir: File, private val projectId: String) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val versionsDir = File(projectDir, "versions").apply { mkdirs() }
    private val historyFile = File(versionsDir, "history.json")

    fun commit(files: ProjectFiles, message: String): VersionEntry {
        val history = readHistory()
        val nextVersion = (history.versions.maxOfOrNull { it.versionNumber } ?: 0) + 1
        val now = System.currentTimeMillis()

        val entry = VersionEntry(
            versionNumber = nextVersion,
            timestamp = now,
            message = message
        )

        val snapshot = VersionSnapshot(
            versionNumber = nextVersion,
            timestamp = now,
            message = message,
            manifestJson = files.manifestJson,
            stateJson = files.stateJson,
            uiJson = files.uiJson,
            actionsJson = files.actionsJson
        )

        // Write snapshot file
        val snapshotFile = File(versionsDir, "v${nextVersion.toString().padStart(3, '0')}.json")
        snapshotFile.writeText(json.encodeToString(snapshot))

        // Update history
        val updated = history.copy(versions = history.versions + entry)
        historyFile.writeText(json.encodeToString(updated))

        return entry
    }

    fun listHistory(): List<VersionEntry> {
        return readHistory().versions.sortedByDescending { it.versionNumber }
    }

    fun getVersion(versionNumber: Int): VersionSnapshot? {
        val snapshotFile = File(versionsDir, "v${versionNumber.toString().padStart(3, '0')}.json")
        if (!snapshotFile.exists()) return null
        return try {
            json.decodeFromString<VersionSnapshot>(snapshotFile.readText())
        } catch (_: Exception) {
            null
        }
    }

    fun revert(versionNumber: Int): ProjectFiles? {
        val snapshot = getVersion(versionNumber) ?: return null
        return ProjectFiles(
            manifestJson = snapshot.manifestJson,
            stateJson = snapshot.stateJson,
            uiJson = snapshot.uiJson,
            actionsJson = snapshot.actionsJson
        )
    }

    fun diff(fromVersion: Int, toVersion: Int): VersionDiff? {
        val from = getVersion(fromVersion) ?: return null
        val to = getVersion(toVersion) ?: return null
        return VersionDiff(
            fromVersion = fromVersion,
            toVersion = toVersion,
            stateChanged = from.stateJson != to.stateJson,
            uiChanged = from.uiJson != to.uiJson,
            actionsChanged = from.actionsJson != to.actionsJson,
            manifestChanged = from.manifestJson != to.manifestJson
        )
    }

    private fun readHistory(): VersionHistory {
        if (!historyFile.exists()) return VersionHistory(projectId = projectId)
        return try {
            json.decodeFromString<VersionHistory>(historyFile.readText())
        } catch (_: Exception) {
            VersionHistory(projectId = projectId)
        }
    }
}

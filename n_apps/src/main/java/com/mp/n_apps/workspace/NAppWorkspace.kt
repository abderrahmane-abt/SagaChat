package com.mp.n_apps.workspace

import android.content.Context
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

class NAppWorkspace(context: Context) {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }
    private val rootDir = File(context.filesDir, "napp_projects").apply { mkdirs() }
    private val indexFile = File(context.filesDir, "napp_project_index.json")

    // ── Project CRUD ──

    fun listProjects(): List<ProjectMetadata> {
        return readIndex().projects
    }

    fun createProject(name: String, description: String = ""): ProjectMetadata {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val metadata = ProjectMetadata(
            id = id,
            name = name,
            description = description,
            createdAt = now,
            lastModifiedAt = now
        )

        val projectDir = File(rootDir, id).apply { mkdirs() }

        // Write default files
        File(projectDir, "manifest.json").writeText(DEFAULT_MANIFEST.replace("{APP_NAME}", name))
        File(projectDir, "state.json").writeText(DEFAULT_STATE)
        File(projectDir, "ui.json").writeText(DEFAULT_UI)
        File(projectDir, "actions.json").writeText(DEFAULT_ACTIONS)

        // Update index
        val index = readIndex()
        writeIndex(index.copy(projects = index.projects + metadata))

        return metadata
    }

    fun openProject(id: String): ProjectFiles? {
        val projectDir = File(rootDir, id)
        if (!projectDir.exists()) return null

        return ProjectFiles(
            manifestJson = File(projectDir, "manifest.json").readTextOrDefault("{}"),
            stateJson = File(projectDir, "state.json").readTextOrDefault("{}"),
            uiJson = File(projectDir, "ui.json").readTextOrDefault("{\"components\":[]}"),
            actionsJson = File(projectDir, "actions.json").readTextOrDefault("{\"actions\":{}}")
        )
    }

    fun saveProject(id: String, files: ProjectFiles) {
        val projectDir = File(rootDir, id)
        if (!projectDir.exists()) return

        File(projectDir, "manifest.json").writeText(files.manifestJson)
        File(projectDir, "state.json").writeText(files.stateJson)
        File(projectDir, "ui.json").writeText(files.uiJson)
        File(projectDir, "actions.json").writeText(files.actionsJson)

        // Update lastModifiedAt in index
        val index = readIndex()
        val updated = index.projects.map { meta ->
            if (meta.id == id) meta.copy(lastModifiedAt = System.currentTimeMillis())
            else meta
        }
        writeIndex(index.copy(projects = updated))
    }

    fun deleteProject(id: String) {
        File(rootDir, id).deleteRecursively()

        val index = readIndex()
        writeIndex(index.copy(projects = index.projects.filter { it.id != id }))
    }

    fun getProjectDir(id: String): File = File(rootDir, id)

    // ── Internal ──

    private fun readIndex(): ProjectIndex {
        if (!indexFile.exists()) return ProjectIndex()
        return try {
            json.decodeFromString<ProjectIndex>(indexFile.readText())
        } catch (_: Exception) {
            ProjectIndex()
        }
    }

    private fun writeIndex(index: ProjectIndex) {
        indexFile.writeText(json.encodeToString(index))
    }

    private fun File.readTextOrDefault(default: String): String {
        return if (exists()) readText() else default
    }

    companion object {
        private val DEFAULT_MANIFEST = """
{
  "app": {
    "name": "{APP_NAME}",
    "version": "1.0.0"
  }
}
        """.trimIndent()

        private val DEFAULT_STATE = """
{
  "schema": {}
}
        """.trimIndent()

        private val DEFAULT_UI = """
{
  "components": []
}
        """.trimIndent()

        private val DEFAULT_ACTIONS = """
{
  "actions": {}
}
        """.trimIndent()
    }
}

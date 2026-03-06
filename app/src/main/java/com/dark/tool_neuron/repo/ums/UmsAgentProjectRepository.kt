package com.dark.tool_neuron.repo.ums

import com.dark.tool_neuron.data.Tags
import com.dark.tool_neuron.data.UmsCollections
import com.dark.tool_neuron.models.agent.AgentProject
import com.dark.tool_neuron.models.agent.ProjectStatus
import com.dark.ums.UmsRecord
import com.dark.ums.UnifiedMemorySystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class UmsAgentProjectRepository(private val ums: UnifiedMemorySystem) {

    private val collection = UmsCollections.AGENT_PROJECTS

    private val _allProjects = MutableStateFlow<List<AgentProject>>(emptyList())
    val allProjects: Flow<List<AgentProject>> = _allProjects

    fun init() {
        ums.ensureCollection(collection)
        ums.addIndex(collection, Tags.AgentProject.ENTITY_ID, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(collection, Tags.AgentProject.STATUS, UnifiedMemorySystem.WIRE_BYTES)
        ums.addIndex(collection, Tags.AgentProject.LAST_OPENED_AT, UnifiedMemorySystem.WIRE_FIXED64)
        refreshCache()
    }

    // ── CRUD ──

    suspend fun createProject(
        name: String,
        writerModelId: String,
        toolModelId: String? = null,
        description: String = ""
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val project = AgentProject(
            id = id,
            name = name,
            description = description,
            toolModelId = toolModelId,
            writerModelId = writerModelId,
            createdAt = now,
            lastOpenedAt = now,
            messageCount = 0,
            status = ProjectStatus.ACTIVE
        )
        ums.put(collection, project.toRecord())
        refreshCache()
        id
    }

    suspend fun updateProject(project: AgentProject) = withContext(Dispatchers.IO) {
        val recordId = findRecordId(project.id) ?: return@withContext
        ums.put(collection, project.toRecord(recordId))
        refreshCache()
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val recordId = findRecordId(projectId) ?: return@withContext
        ums.delete(collection, recordId)
        refreshCache()
    }

    suspend fun getById(projectId: String): AgentProject? = withContext(Dispatchers.IO) {
        ums.queryString(collection, Tags.AgentProject.ENTITY_ID, projectId)
            .firstOrNull()?.toAgentProject()
    }

    suspend fun touchLastOpened(projectId: String) = withContext(Dispatchers.IO) {
        val record = ums.queryString(collection, Tags.AgentProject.ENTITY_ID, projectId)
            .firstOrNull() ?: return@withContext
        val project = record.toAgentProject().copy(lastOpenedAt = System.currentTimeMillis())
        ums.put(collection, project.toRecord(record.id))
        refreshCache()
    }

    suspend fun incrementMessageCount(projectId: String) = withContext(Dispatchers.IO) {
        val record = ums.queryString(collection, Tags.AgentProject.ENTITY_ID, projectId)
            .firstOrNull() ?: return@withContext
        val project = record.toAgentProject()
        val updated = project.copy(messageCount = project.messageCount + 1)
        ums.put(collection, updated.toRecord(record.id))
        refreshCache()
    }

    // ── Helpers ──

    private fun findRecordId(entityId: String): Int? =
        ums.queryString(collection, Tags.AgentProject.ENTITY_ID, entityId)
            .firstOrNull()?.id

    private fun refreshCache() {
        _allProjects.value = ums.getAll(collection)
            .map { it.toAgentProject() }
            .sortedByDescending { it.lastOpenedAt }
    }

    // ── Serialization ──

    private fun AgentProject.toRecord(existingId: Int = 0): UmsRecord {
        val b = UmsRecord.create()
        if (existingId != 0) b.id(existingId)
        b.putString(Tags.AgentProject.ENTITY_ID, id)
        b.putString(Tags.AgentProject.NAME, name)
        b.putString(Tags.AgentProject.DESCRIPTION, description)
        if (toolModelId != null) b.putString(Tags.AgentProject.TOOL_MODEL_ID, toolModelId)
        b.putString(Tags.AgentProject.WRITER_MODEL_ID, writerModelId)
        b.putTimestamp(Tags.AgentProject.CREATED_AT, createdAt)
        b.putTimestamp(Tags.AgentProject.LAST_OPENED_AT, lastOpenedAt)
        b.putInt(Tags.AgentProject.MESSAGE_COUNT, messageCount)
        b.putString(Tags.AgentProject.STATUS, status.name)
        return b.build()
    }

    private fun UmsRecord.toAgentProject(): AgentProject = AgentProject(
        id = getString(Tags.AgentProject.ENTITY_ID) ?: "",
        name = getString(Tags.AgentProject.NAME) ?: "",
        description = getString(Tags.AgentProject.DESCRIPTION) ?: "",
        toolModelId = getString(Tags.AgentProject.TOOL_MODEL_ID),
        writerModelId = getString(Tags.AgentProject.WRITER_MODEL_ID) ?: "",
        createdAt = getTimestamp(Tags.AgentProject.CREATED_AT) ?: 0L,
        lastOpenedAt = getTimestamp(Tags.AgentProject.LAST_OPENED_AT) ?: 0L,
        messageCount = getInt(Tags.AgentProject.MESSAGE_COUNT) ?: 0,
        status = ProjectStatus.from(getString(Tags.AgentProject.STATUS) ?: "ACTIVE")
    )
}

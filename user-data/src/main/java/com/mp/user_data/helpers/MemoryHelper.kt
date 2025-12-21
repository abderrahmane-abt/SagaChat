package com.mp.user_data.helpers

import android.util.Log
import com.mp.user_data.ntds.neuron_tree.NeuronTree
import com.mp.user_data.ntds.neuron_tree.NodeType
import org.json.JSONObject

/**
 * Helper for managing long-term memories and facts.
 * Memories are stored as individual nodes with metadata.
 *
 * Usage:
 * ```kotlin
 * val memoryHelper = MemoryHelper(tree)
 * val memoryId = memoryHelper.addMemory(
 *     content = "User prefers dark mode",
 *     category = "preference",
 *     tags = listOf("ui", "settings")
 * )
 * val memories = memoryHelper.getMemoriesByCategory("preference")
 * ```
 */
class MemoryHelper(tree: NeuronTree) : BaseHelper(tree, "memoryHistory") {

    /**
     * Add a new memory.
     * @param content The memory content
     * @param category Optional category (e.g., "preference", "fact", "context")
     * @param tags Optional list of tags for organization
     * @param metadata Optional additional metadata
     * @return The ID of the newly created memory
     */
    fun addMemory(
        content: String,
        category: String = "general",
        tags: List<String> = emptyList(),
        metadata: Map<String, String> = emptyMap()
    ): String {
        val memoryData = JSONObject().apply {
            put("content", content)
            put("category", category)
            put("tags", tags.joinToString(","))
            put("createdAt", System.currentTimeMillis())
            put("accessCount", 0)
            put("lastAccessed", System.currentTimeMillis())
            
            // Add custom metadata
            if (metadata.isNotEmpty()) {
                val metaJson = JSONObject()
                metadata.forEach { (key, value) ->
                    metaJson.put(key, value)
                }
                put("metadata", metaJson)
            }
        }
        
        val memoryId = addChildNode(memoryData.toString(), NodeType.LEAF)
        Log.d(TAG, "Added memory: $content (ID: $memoryId)")
        return memoryId
    }

    /**
     * Get all memories.
     * @return List of Memory objects
     */
    fun getAllMemories(): List<Memory> {
        return getAllChildren().mapNotNull { node ->
            parseNodeContent(node)?.let { json ->
                Memory(
                    id = node.id,
                    content = json.getString("content"),
                    category = json.optString("category", "general"),
                    tags = json.optString("tags", "")
                        .split(",")
                        .filter { it.isNotBlank() },
                    createdAt = json.optLong("createdAt", 0),
                    accessCount = json.optInt("accessCount", 0),
                    lastAccessed = json.optLong("lastAccessed", 0),
                    metadata = json.optJSONObject("metadata")?.let { metaJson ->
                        val map = mutableMapOf<String, String>()
                        metaJson.keys().forEach { key ->
                            map[key] = metaJson.getString(key)
                        }
                        map
                    } ?: emptyMap()
                )
            }
        }
    }

    /**
     * Get a specific memory by ID.
     * Increments access count and updates last accessed time.
     * @return Memory if found, null otherwise
     */
    fun getMemory(memoryId: String): Memory? {
        val node = getNodeById(memoryId) ?: return null
        val json = parseNodeContent(node) ?: return null
        
        // Update access tracking
        val accessCount = json.optInt("accessCount", 0) + 1
        json.put("accessCount", accessCount)
        json.put("lastAccessed", System.currentTimeMillis())
        updateNodeContent(memoryId, json.toString())
        
        return Memory(
            id = node.id,
            content = json.getString("content"),
            category = json.optString("category", "general"),
            tags = json.optString("tags", "")
                .split(",")
                .filter { it.isNotBlank() },
            createdAt = json.optLong("createdAt", 0),
            accessCount = accessCount,
            lastAccessed = json.optLong("lastAccessed", 0),
            metadata = json.optJSONObject("metadata")?.let { metaJson ->
                val map = mutableMapOf<String, String>()
                metaJson.keys().forEach { key ->
                    map[key] = metaJson.getString(key)
                }
                map
            } ?: emptyMap()
        )
    }

    /**
     * Get memories by category.
     * @param category The category to filter by
     * @return List of matching memories
     */
    fun getMemoriesByCategory(category: String): List<Memory> {
        return getAllMemories().filter { it.category == category }
    }

    /**
     * Get memories by tag.
     * @param tag The tag to filter by
     * @return List of memories containing the tag
     */
    fun getMemoriesByTag(tag: String): List<Memory> {
        return getAllMemories().filter { tag in it.tags }
    }

    /**
     * Search memories by content.
     * @param query The search query
     * @return List of matching memories
     */
    fun searchMemories(query: String): List<Memory> {
        val lowerQuery = query.lowercase()
        return getAllMemories().filter { 
            it.content.lowercase().contains(lowerQuery)
        }
    }

    /**
     * Update the content of a memory.
     * @return true if successful, false if memory not found
     */
    fun updateMemoryContent(memoryId: String, newContent: String): Boolean {
        val node = getNodeById(memoryId) ?: return false
        val json = parseNodeContent(node) ?: return false
        
        json.put("content", newContent)
        
        return updateNodeContent(memoryId, json.toString())
    }

    /**
     * Update the tags of a memory.
     * @return true if successful, false if memory not found
     */
    fun updateMemoryTags(memoryId: String, tags: List<String>): Boolean {
        val node = getNodeById(memoryId) ?: return false
        val json = parseNodeContent(node) ?: return false
        
        json.put("tags", tags.joinToString(","))
        
        return updateNodeContent(memoryId, json.toString())
    }

    /**
     * Delete a memory by ID.
     * @return true if successful, false if memory not found
     */
    fun deleteMemory(memoryId: String): Boolean {
        return deleteNode(memoryId)
    }

    /**
     * Get the most frequently accessed memories.
     * @param limit Maximum number of memories to return
     * @return List of memories sorted by access count (highest first)
     */
    fun getMostAccessedMemories(limit: Int = 10): List<Memory> {
        return getAllMemories()
            .sortedByDescending { it.accessCount }
            .take(limit)
    }

    /**
     * Get memories created within a time range.
     * @param startTime Start timestamp (inclusive)
     * @param endTime End timestamp (inclusive)
     * @return List of memories created in the range
     */
    fun getMemoriesInTimeRange(startTime: Long, endTime: Long): List<Memory> {
        return getAllMemories().filter { 
            it.createdAt in startTime..endTime
        }
    }

    /**
     * Get all unique categories.
     * @return List of category names
     */
    fun getAllCategories(): List<String> {
        return getAllMemories()
            .map { it.category }
            .distinct()
            .sorted()
    }

    /**
     * Get all unique tags.
     * @return List of tag names
     */
    fun getAllTags(): List<String> {
        return getAllMemories()
            .flatMap { it.tags }
            .distinct()
            .sorted()
    }
}

/**
 * Represents a single memory entry.
 */
data class Memory(
    val id: String,
    val content: String,
    val category: String,
    val tags: List<String>,
    val createdAt: Long,
    val accessCount: Int,
    val lastAccessed: Long,
    val metadata: Map<String, String>
)
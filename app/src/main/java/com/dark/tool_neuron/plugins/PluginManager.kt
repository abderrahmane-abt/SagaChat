package com.dark.tool_neuron.plugins

import android.util.Log
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolDefinitionBuilder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object PluginManager {

    private const val TAG = "PluginManager"

    // Registry of all plugins
    private val _plugins = mutableMapOf<String, SuperPlugin>()

    // Set of enabled plugin names
    private val _enabledPluginNames = MutableStateFlow<Set<String>>(emptySet())
    val enabledPluginNames: StateFlow<Set<String>> = _enabledPluginNames.asStateFlow()

    // List of registered plugins
    private val _registeredPlugins = MutableStateFlow<List<PluginInfo>>(emptyList())
    val registeredPlugins: StateFlow<List<PluginInfo>> = _registeredPlugins.asStateFlow()

    /**
     * Register a plugin
     */
    fun registerPlugin(plugin: SuperPlugin) {
        val pluginInfo = plugin.getPluginInfo()
        _plugins[pluginInfo.name] = plugin
        updateRegisteredPlugins()
    }

    /**
     * Enable a plugin
     */
    fun enablePlugin(pluginName: String) {
        if (_plugins.containsKey(pluginName)) {
            _enabledPluginNames.value = _enabledPluginNames.value + pluginName
            syncToolsWithLLM()
        }
    }

    /**
     * Disable a plugin
     */
    fun disablePlugin(pluginName: String) {
        _enabledPluginNames.value = _enabledPluginNames.value - pluginName
        syncToolsWithLLM()
    }

    /**
     * Toggle plugin enabled state
     */
    fun togglePlugin(pluginName: String, enabled: Boolean) {
        if (enabled) {
            enablePlugin(pluginName)
        } else {
            disablePlugin(pluginName)
        }
    }

    /**
     * Manually sync enabled plugin tools with the LLM
     * Call this after loading a model to ensure tools are registered
     */
    fun syncToolsWithLLM() {
        val toolDefinitions = getEnabledToolDefinitions()

        // Check model info
        val modelInfo = LlmModelWorker.getGgufModelInfo()
        Log.d(TAG, "Current model info: $modelInfo")

        if (toolDefinitions.isEmpty()) {
            // No tools enabled, clear all tools
            LlmModelWorker.clearToolsGguf()
            Log.d(TAG, "Cleared all tools from LLM")
        } else {
            // Convert tool definitions to JSON and send to LLM
            val toolsJson = convertToolsToJson(toolDefinitions)
            Log.d(TAG, "Tools JSON (${toolsJson.length} chars): $toolsJson")

            val success = LlmModelWorker.setToolsGguf(toolsJson)
            if (success) {
                Log.d(TAG, "✅ Synced ${toolDefinitions.size} tools with LLM")
                Log.i(TAG, "Tool calling is enabled. Model must be Qwen for this to work!")
            } else {
                Log.e(TAG, "❌ Failed to sync tools with LLM")
            }
        }
    }

    /**
     * Convert tool definitions to OpenAI-format JSON
     */
    private fun convertToolsToJson(toolDefinitions: List<ToolDefinitionBuilder>): String {
        val toolsArray = JSONArray()

        for (toolDef in toolDefinitions) {
            val toolJson = JSONObject().apply {
                put("type", "function")
                put("function", toolDef.build().toOpenAIFormat())
            }
            toolsArray.put(toolJson)
        }

        return toolsArray.toString()
    }

    /**
     * Get a plugin by name
     */
    fun getPlugin(pluginName: String): SuperPlugin? {
        return _plugins[pluginName]
    }

    /**
     * Check if a plugin is enabled
     */
    fun isPluginEnabled(pluginName: String): Boolean {
        return _enabledPluginNames.value.contains(pluginName)
    }

    /**
     * Get tool definitions for all enabled plugins
     */
    fun getEnabledToolDefinitions(): List<ToolDefinitionBuilder> {
        return _enabledPluginNames.value.flatMap { pluginName ->
            _plugins[pluginName]?.getPluginInfo()?.toolDefinitionBuilder ?: emptyList()
        }
    }

    /**
     * Execute a tool call
     */
    suspend fun executeTool(toolCall: ToolCall): PluginExecutionResult {
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "🔧 Tool call received: ${toolCall.name} with args: ${toolCall.arguments}")

        // Find which plugin owns this tool (case-insensitive matching)
        val pluginEntry = _plugins.entries.find { (_, plugin) ->
            plugin.getPluginInfo().toolDefinitionBuilder.any {
                it.name.equals(toolCall.name, ignoreCase = true)
            }
        }

        if (pluginEntry == null) {
            Log.e(TAG, "❌ Tool not found: ${toolCall.name}")
            Log.d(TAG, "Available tools: ${_plugins.values.flatMap { it.getPluginInfo().toolDefinitionBuilder.map { t -> t.name } }}")
            val metrics = PluginExecutionMetrics(
                pluginName = "Unknown",
                toolName = toolCall.name,
                executionTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                errorMessage = "Tool not found: ${toolCall.name}"
            )
            return PluginExecutionResult.Failure(metrics, null)
        }

        val (_, plugin) = pluginEntry
        val pluginInfo = plugin.getPluginInfo()

        // Check if plugin is enabled
        if (!isPluginEnabled(pluginInfo.name)) {
            val metrics = PluginExecutionMetrics(
                pluginName = pluginInfo.name,
                toolName = toolCall.name,
                executionTimeMs = System.currentTimeMillis() - startTime,
                success = false,
                errorMessage = "Plugin is not enabled: ${pluginInfo.name}"
            )
            return PluginExecutionResult.Failure(metrics, null)
        }

        // Execute the tool
        return try {
            val result = plugin.executeTool(toolCall)
            val executionTime = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val data = result.getOrNull()
                val metrics = PluginExecutionMetrics(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    executionTimeMs = executionTime,
                    success = true
                )

                // Convert data to JSON string
                val resultDataJson = convertDataToJson(data)

                val resultData = PluginResultData(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    inputParams = toolCall.arguments.toString(),
                    resultData = resultDataJson,
                    success = true
                )

                PluginExecutionResult.Success(metrics, resultData, data!!)
            } else {
                val error = result.exceptionOrNull()
                val metrics = PluginExecutionMetrics(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    executionTimeMs = executionTime,
                    success = false,
                    errorMessage = error?.message ?: "Unknown error"
                )

                val resultData = PluginResultData(
                    pluginName = pluginInfo.name,
                    toolName = toolCall.name,
                    inputParams = toolCall.arguments.toString(),
                    resultData = error?.message ?: "Unknown error",
                    success = false
                )

                PluginExecutionResult.Failure(metrics, resultData)
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            val metrics = PluginExecutionMetrics(
                pluginName = pluginInfo.name,
                toolName = toolCall.name,
                executionTimeMs = executionTime,
                success = false,
                errorMessage = e.message ?: "Unknown error"
            )

            val resultData = PluginResultData(
                pluginName = pluginInfo.name,
                toolName = toolCall.name,
                inputParams = toolCall.arguments.toString(),
                resultData = e.message ?: "Unknown error",
                success = false
            )

            PluginExecutionResult.Failure(metrics, resultData)
        }
    }

    /**
     * Update the list of registered plugins
     */
    private fun updateRegisteredPlugins() {
        _registeredPlugins.value = _plugins.values.map { it.getPluginInfo() }
    }

    /**
     * Convert plugin result data to JSON string
     */
    private fun convertDataToJson(data: Any?): String {
        if (data == null) return "{}"

        return try {
            // Handle known types from WebSearchPlugin
            when (data) {
                is com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse -> {
                    JSONObject().apply {
                        put("query", data.query)
                        put("totalResults", data.totalResults)
                        put("searchTime", data.searchTime)

                        val resultsArray = JSONArray()
                        data.results.forEach { result ->
                            resultsArray.put(JSONObject().apply {
                                put("title", result.title)
                                put("snippet", result.snippet)
                                put("url", result.url)
                                put("position", result.position)
                            })
                        }
                        put("results", resultsArray)
                    }.toString()
                }
                is com.dark.tool_neuron.models.plugins.ScrapedContent -> {
                    JSONObject().apply {
                        put("url", data.url)
                        put("title", data.title)
                        put("content", data.content)
                        put("contentLength", data.contentLength)
                        put("fetchTime", data.fetchTime)

                        val metadataObj = JSONObject()
                        data.metadata.forEach { (key, value) ->
                            metadataObj.put(key, value)
                        }
                        put("metadata", metadataObj)
                    }.toString()
                }
                else -> {
                    // Fallback: use toString()
                    Log.w(TAG, "Unknown data type: ${data::class.simpleName}, using toString()")
                    data.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to convert data to JSON: ${e.message}")
            data.toString()
        }
    }
}

/**
 * Result of plugin execution
 */
sealed class PluginExecutionResult {
    data class Success(
        val metrics: PluginExecutionMetrics,
        val resultData: PluginResultData,
        val data: Any
    ) : PluginExecutionResult()

    data class Failure(
        val metrics: PluginExecutionMetrics,
        val resultData: PluginResultData?
    ) : PluginExecutionResult()
}

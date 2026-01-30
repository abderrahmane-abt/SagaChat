package com.dark.tool_neuron.plugins

import android.util.Log
import com.dark.tool_neuron.models.plugins.PluginExecutionMetrics
import com.dark.tool_neuron.models.plugins.PluginInfo
import com.dark.tool_neuron.models.plugins.PluginResultData
import com.dark.tool_neuron.plugins.api.SuperPlugin
import com.dark.tool_neuron.worker.LlmModelWorker
import com.mp.ai_gguf.toolcalling.GrammarMode
import com.mp.ai_gguf.toolcalling.ToolCall
import com.mp.ai_gguf.toolcalling.ToolCallingConfig
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

    // Tool calling configuration
    private val _toolCallingConfig = MutableStateFlow(ToolCallingConfig())
    val toolCallingConfig: StateFlow<ToolCallingConfig> = _toolCallingConfig.asStateFlow()

    // Grammar mode
    private val _grammarMode = MutableStateFlow(GrammarMode.LAZY)
    val grammarMode: StateFlow<GrammarMode> = _grammarMode.asStateFlow()

    // Whether multi-turn is active
    private val _multiTurnEnabled = MutableStateFlow(true)
    val multiTurnEnabled: StateFlow<Boolean> = _multiTurnEnabled.asStateFlow()

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
     * Set grammar mode for tool calling
     */
    fun setGrammarMode(mode: GrammarMode) {
        _grammarMode.value = mode
        LlmModelWorker.setGrammarModeGguf(mode.value)
        Log.d(TAG, "Grammar mode set to ${mode.name}")
    }

    /**
     * Set multi-turn tool calling enabled state
     */
    fun setMultiTurnEnabled(enabled: Boolean) {
        _multiTurnEnabled.value = enabled
        Log.d(TAG, "Multi-turn tool calling: ${if (enabled) "enabled" else "disabled"}")
    }

    /**
     * Update tool calling configuration
     */
    fun updateToolCallingConfig(config: ToolCallingConfig) {
        _toolCallingConfig.value = config
        // Re-sync with new config
        syncToolsWithLLM()
    }

    /**
     * Get current tool calling config
     */
    fun getToolCallingConfig(): ToolCallingConfig = _toolCallingConfig.value

    /**
     * Manually sync enabled plugin tools with the LLM.
     * Now uses enableToolCallingGguf() with grammar configuration.
     * Works with any model that has a chat template (model-agnostic).
     */
    fun syncToolsWithLLM() {
        val toolDefinitions = getEnabledToolDefinitions()

        val modelInfo = LlmModelWorker.getGgufModelInfo()
        Log.d(TAG, "Current model info: $modelInfo")

        if (toolDefinitions.isEmpty()) {
            LlmModelWorker.clearToolsGguf()
            Log.d(TAG, "Cleared all tools from LLM")
        } else {
            val toolsJson = convertToolsToJson(toolDefinitions)
            Log.d(TAG, "Tools JSON (${toolsJson.length} chars): $toolsJson")

            val config = _toolCallingConfig.value
            val mode = _grammarMode.value

            val success = LlmModelWorker.enableToolCallingGguf(
                toolsJson = toolsJson,
                grammarMode = mode.value,
                useTypedGrammar = config.useTypedGrammar
            )

            if (success) {
                Log.d(TAG, "Synced ${toolDefinitions.size} tools with LLM " +
                        "(grammar=${mode.name}, typed=${config.useTypedGrammar})")
            } else {
                Log.e(TAG, "Failed to sync tools with LLM")
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
     * Check if any tools are currently enabled
     */
    fun hasEnabledTools(): Boolean = getEnabledToolDefinitions().isNotEmpty()

    /**
     * Execute a tool call and return result in SDK ToolResult format for multi-turn.
     * Returns a JSON string that can be appended as a "tool" message in the conversation.
     */
    suspend fun executeToolForMultiTurn(toolCall: ToolCall): MultiTurnToolResult {
        val startTime = System.currentTimeMillis()
        Log.d(TAG, "Multi-turn tool call: ${toolCall.name} with args: ${toolCall.arguments}")

        val pluginEntry = _plugins.entries.find { (_, plugin) ->
            plugin.getPluginInfo().toolDefinitionBuilder.any {
                it.name.equals(toolCall.name, ignoreCase = true)
            }
        }

        if (pluginEntry == null) {
            Log.e(TAG, "Tool not found: ${toolCall.name}")
            return MultiTurnToolResult(
                toolName = toolCall.name,
                resultJson = """{"error": "Tool not found: ${toolCall.name}"}""",
                isError = true,
                pluginName = "Unknown",
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        val (_, plugin) = pluginEntry
        val pluginInfo = plugin.getPluginInfo()

        if (!isPluginEnabled(pluginInfo.name)) {
            return MultiTurnToolResult(
                toolName = toolCall.name,
                resultJson = """{"error": "Plugin not enabled: ${pluginInfo.name}"}""",
                isError = true,
                pluginName = pluginInfo.name,
                executionTimeMs = System.currentTimeMillis() - startTime
            )
        }

        return try {
            val result = plugin.executeTool(toolCall)
            val executionTime = System.currentTimeMillis() - startTime

            if (result.isSuccess) {
                val data = result.getOrNull()
                val resultJson = convertDataToJson(data)
                MultiTurnToolResult(
                    toolName = toolCall.name,
                    resultJson = resultJson,
                    isError = false,
                    pluginName = pluginInfo.name,
                    executionTimeMs = executionTime,
                    rawData = data
                )
            } else {
                val error = result.exceptionOrNull()
                MultiTurnToolResult(
                    toolName = toolCall.name,
                    resultJson = """{"error": "${error?.message ?: "Unknown error"}"}""",
                    isError = true,
                    pluginName = pluginInfo.name,
                    executionTimeMs = executionTime
                )
            }
        } catch (e: Exception) {
            val executionTime = System.currentTimeMillis() - startTime
            MultiTurnToolResult(
                toolName = toolCall.name,
                resultJson = """{"error": "${e.message ?: "Unknown error"}"}""",
                isError = true,
                pluginName = pluginInfo.name,
                executionTimeMs = executionTime
            )
        }
    }

    /**
     * Execute a tool call (legacy single-turn API)
     */
    suspend fun executeTool(toolCall: ToolCall): PluginExecutionResult {
        val startTime = System.currentTimeMillis()

        Log.d(TAG, "Tool call received: ${toolCall.name} with args: ${toolCall.arguments}")

        val pluginEntry = _plugins.entries.find { (_, plugin) ->
            plugin.getPluginInfo().toolDefinitionBuilder.any {
                it.name.equals(toolCall.name, ignoreCase = true)
            }
        }

        if (pluginEntry == null) {
            Log.e(TAG, "Tool not found: ${toolCall.name}")
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
                is CalculatorResponse -> {
                    JSONObject().apply {
                        put("expression", data.expression)
                        put("result", data.result)
                        put("formattedResult", data.formattedResult)
                    }.toString()
                }
                is UnitConversionResponse -> {
                    JSONObject().apply {
                        put("value", data.value)
                        put("from_unit", data.fromUnit)
                        put("to_unit", data.toUnit)
                        put("result", data.result)
                        put("formattedResult", data.formattedResult)
                    }.toString()
                }
                is DevUtilsResponse -> {
                    JSONObject().apply {
                        put("tool", data.tool)
                        put("operation", data.operation)
                        put("input", data.input)
                        put("output", data.output)
                    }.toString()
                }
                is TextStatsResponse -> {
                    JSONObject().apply {
                        put("charCount", data.charCount)
                        put("charCountNoSpaces", data.charCountNoSpaces)
                        put("wordCount", data.wordCount)
                        put("lineCount", data.lineCount)
                        put("sentenceCount", data.sentenceCount)
                        put("summary", data.summary)
                    }.toString()
                }
                else -> {
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
 * Result from multi-turn tool execution
 */
data class MultiTurnToolResult(
    val toolName: String,
    val resultJson: String,
    val isError: Boolean,
    val pluginName: String,
    val executionTimeMs: Long,
    val rawData: Any? = null
)

/**
 * Result of plugin execution (legacy)
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

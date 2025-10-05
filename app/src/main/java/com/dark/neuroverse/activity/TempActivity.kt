package com.dark.neuroverse.activity

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.agent.config.AIAgentConfig
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import ai.koog.agents.core.tools.reflect.asTools
import ai.koog.prompt.executor.llms.all.simpleOpenRouterExecutor
import android.os.Environment
import java.io.File
import ai.koog.prompt.llm.LLMCapability
import ai.koog.prompt.llm.LLMProvider
import ai.koog.prompt.llm.LLModel
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import kotlinx.coroutines.launch

class TempActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            NeuroVerseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AgentScreen()
                }
            }
        }
    }
}

@LLMDescription("Tools for file manipulation operations")
class FileTools : ToolSet {

    @Tool
    @LLMDescription("Create a new text file with content in Downloads folder")
    fun createFile(
        @LLMDescription("File name with extension (e.g., note.txt)") fileName: String,
        @LLMDescription("Content to write in the file") content: String
    ): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            file.writeText(content)
            "✅ File created: ${file.absolutePath}"
        } catch (e: Exception) {
            "❌ Error creating file: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Read content from a text file in Downloads folder")
    fun readFile(
        @LLMDescription("File name to read") fileName: String
    ): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            if (file.exists()) {
                "📄 Content of $fileName:\n${file.readText()}"
            } else {
                "❌ File not found: $fileName"
            }
        } catch (e: Exception) {
            "❌ Error reading file: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("List all files in Downloads folder")
    fun listFiles(): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val files = downloadsDir.listFiles()
            if (files.isNullOrEmpty()) {
                "📂 Downloads folder is empty"
            } else {
                "📂 Files in Downloads:\n" + files.joinToString("\n") {
                    "- ${it.name} (${it.length() / 1024} KB)"
                }
            }
        } catch (e: Exception) {
            "❌ Error listing files: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Delete a file from Downloads folder")
    fun deleteFile(
        @LLMDescription("File name to delete") fileName: String
    ): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)
            if (file.exists() && file.delete()) {
                "✅ File deleted: $fileName"
            } else {
                "❌ Could not delete file: $fileName"
            }
        } catch (e: Exception) {
            "❌ Error deleting file: ${e.message}"
        }
    }

    @Tool
    @LLMDescription("Rename a file in Downloads folder")
    fun renameFile(
        @LLMDescription("Current file name") oldName: String,
        @LLMDescription("New file name") newName: String
    ): String {
        return try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val oldFile = File(downloadsDir, oldName)
            val newFile = File(downloadsDir, newName)
            if (oldFile.exists() && oldFile.renameTo(newFile)) {
                "✅ File renamed: $oldName → $newName"
            } else {
                "❌ Could not rename file"
            }
        } catch (e: Exception) {
            "❌ Error renaming file: ${e.message}"
        }
    }
}

@Composable
fun AgentScreen() {
    var response by remember { mutableStateOf("Click button to ask agent") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Initialize agent
    val agent = remember {
        val promptExecutor = simpleOpenRouterExecutor(
            apiKey = "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"
        )

        val toolRegistry = ToolRegistry {
            tools(FileTools().asTools())
        }

        val agentStrategy = strategy("File management agent") {
            val nodeSendInput by nodeLLMRequest()
            val nodeExecuteTool by nodeExecuteTool()
            val nodeSendToolResult by nodeLLMSendToolResult()

            // Start -> Send input to LLM
            edge(nodeStart forwardTo nodeSendInput)

            // If LLM responds without tools, finish
            edge(
                (nodeSendInput forwardTo nodeFinish)
                        transformed { it }
                        onAssistantMessage { true }
            )

            // If LLM requests tool, execute it
            edge(
                (nodeSendInput forwardTo nodeExecuteTool)
                        onToolCall { true }
            )

            // Send tool result back to LLM
            edge(nodeExecuteTool forwardTo nodeSendToolResult)

            // After tool result, LLM might need another tool
            edge(
                (nodeSendToolResult forwardTo nodeExecuteTool)
                        onToolCall { true }
            )

            // Finish after LLM processes tool result
            edge(
                (nodeSendToolResult forwardTo nodeFinish)
                        transformed { it }
                        onAssistantMessage { true }
            )
        }

        val agentConfig = AIAgentConfig.withSystemPrompt(
            prompt = """
                You are a helpful file management assistant.
                You can create, read, list, delete, and rename files in the Downloads folder.
                All file operations happen in the Downloads folder automatically.
                When users ask about files, use the appropriate tools to help them.
                Be clear and concise in your responses.
            """.trimIndent(),
            llm = LLModel(
                provider = LLMProvider.OpenRouter,
                id = "deepseek/deepseek-chat-v3.1:free",
                capabilities = listOf(
                    LLMCapability.Temperature,
                    LLMCapability.Tools,
                    LLMCapability.Completion
                ),
                contextLength = 32_768,
            ),
            maxAgentIterations = 10
        )

        AIAgent(
            promptExecutor = promptExecutor,
            strategy = agentStrategy,
            agentConfig = agentConfig,
            toolRegistry = toolRegistry
        )
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(
                onClick = {
                    scope.launch {
                        isLoading = true
                        response = ""
                        try {
                            val result = agent.run("Create a file called test.txt with content 'Hello Koog!'")
                            response = result
                            Log.d("Agent", "Result: $result")
                        } catch (e: Exception) {
                            response = "Error: ${e.message}"
                            Log.e("Agent", "Error", e)
                        } finally {
                            isLoading = false
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text(if (isLoading) "Loading..." else "Ask Agent")
            }

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Text(
                    text = response,
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(scrollState)
                )
            }
        }
    }
}
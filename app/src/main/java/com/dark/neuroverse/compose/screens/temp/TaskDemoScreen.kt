package com.dark.neuroverse.compose.screens.temp

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.ai_manager.ai.local.Neuron
import com.dark.task_manager.data.taskRouterSystemPrompt
import com.dark.task_manager.register.TaskRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TaskDemoScreen(paddingValues: PaddingValues) {
    var text by remember { mutableStateOf("Hey Bro I just Got a Job at google as an android developer") }
    var response by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var thinkingContent by remember { mutableStateOf("") }
    var finalReply by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Ask AI") },
            placeholder = { Text("Say Anything") },
            modifier = Modifier.padding(16.dp)
        )

        Button(onClick = {
            context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        }) {
            Text("Open Notification Settings")
        }

        Button(onClick = {
            response = ""
            finalReply = ""
            isThinking = false
            thinkingContent = ""

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userPrompt = text
                    val taskList = TaskRegistry.getTasks()

                    val input = buildString {
                        appendLine(demoPrompt)
                        appendLine()
                        appendLine("User Prompt: $userPrompt")
                    }

                    var isThinkingTagActive = false
                    var tempThinkingBuffer = ""

                    Neuron.generateResponseStreaming(input) { partial ->

                        when {
                            partial.contains("<think>", ignoreCase = true) -> {
                                isThinking = true
                                isThinkingTagActive = true

                                // Clean tag & capture content if any after <think>
                                val afterTag = partial.substringAfter("<think>").trim()
                                if (afterTag.isNotBlank() && !afterTag.contains("</think>", ignoreCase = true)) {
                                    thinkingContent += afterTag
                                    tempThinkingBuffer += afterTag
                                }
                            }

                            partial.contains("</think>", ignoreCase = true) -> {
                                // Content before </think>
                                val beforeEnd = partial.substringBefore("</think>").trim()
                                if (beforeEnd.isNotBlank()) {
                                    thinkingContent += beforeEnd
                                    tempThinkingBuffer += beforeEnd
                                }

                                isThinking = false
                                isThinkingTagActive = false

                                // Content after </think> is normal reply
                                val afterEnd = partial.substringAfter("</think>").trim()
                                if (afterEnd.isNotBlank()) {
                                    response += afterEnd
                                    finalReply = response.trim()
                                }
                            }

                            isThinkingTagActive -> {
                                // Inside <think> zone
                                thinkingContent += partial
                                tempThinkingBuffer += partial
                            }

                            else -> {
                                // Normal response outside <think>
                                response += partial
                                finalReply = response.trim()
                            }
                        }
                    }


                } catch (e: Exception) {
                    println("Error loading model: ${e.message}")
                }
            }
        }) {
            Text("Run Task")
        }

        Spacer(Modifier.height(24.dp))

        if (isThinking) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Thinking...", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = thinkingContent,
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        } else if (finalReply.isNotBlank()) {
            Text(
                text = finalReply,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}


val demoPrompt = """
    WE HAVE A TREE STRUCTURE WHICH IS CALLED BRAIN TREE
    
    YOUR ARE A MCP AGENT, AND YOUR JOB IS TO IDENTIFY THE USER INPUT AND CALL NECESSARY TOOLS TO INSERT THE USER DATA FROM THE PROMPT, LET'S SAY USER I SHARING SOMETHING ABOUT HIS 
    PERSONAL LIFE THEN YOU HAVE TO IDENTIFY IT AND CALL THE APPROPRIATE BRAIN TREE TOOL TO INSERT IT.
    
    RULES:
    1. DON'T OVER-THINK
    2. PROVIDE ONLY TOOL DATA NOT MUCH TEXT
    3. CAN USE MULTIPLE TOOLS AT A TIME 
    4. YOU CAN CREATED YOUR OWN JSON SCHEMA FOR TOOLS:CONTENT AS YOU ARE GOING TO INTERACT WITH BRAIN-TREE
    
    META DATA: BRAIN TREE STRUCTURES
    1. NODE-DATA(NAME: STRING, CONTENT: JSON) //THIS CONTENT IS A DYNAMIC JSON MEANING IT HAS NO FIX SCHEMA SO IT CAN BE ANYTHING
    
    THE LIST OF TOOLS :
    1. ReadBrainTree() -> READS THE ENTIRE BRAIN TREE 
    2. SearchForNode(Node-Name: STRING):BRAIN NODE -> SEARCHES FOR THE NODE WITH THE GIVEN NAME AND RETURNS IT
    3. DeleteNode(Node-Name: STRING):BRAIN NODE -> DELETES THE NODE WITH THE GIVEN NAME
    4. InsertNode(NODE-DATA: NODE):BRAIN NODE -> CREATES A NEW NODE WITH THE GIVEN NAME AND DESCRIPTION
    5. UpdateNode(Node-Name: STRING, Node-Description: STRING):BRAIN NODE -> UPDATES THE NODE WITH THE GIVEN NAME AND DESCRIPTION
    
""".trimIndent()
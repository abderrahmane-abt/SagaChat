package com.dark.neuroverse.compose.screens.temp

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.dark.ai_manager.ai.local.Neuron
import com.dark.task_manager.register.TaskRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun TaskDemoScreen(paddingValues: PaddingValues) {
    var text by remember { mutableStateOf("Hey Bro ..!") }
    var response by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    var thinkingContent by remember { mutableStateOf("") }
    var finalReply by remember { mutableStateOf("") }
    var isThinkingTagActive by remember { mutableStateOf(false) }

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
            isThinkingTagActive = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    TaskRegistry.getTasks()

                    val formattedPrompt = """
                        <|begin_of_text|><|start_header_id|>system<|end_header_id|>
                        $demoPrompt
                        <|eot_id|>
                        <|start_header_id|>user<|end_header_id|>
                        $text
                        <|eot_id|>
                        <|start_header_id|>assistant<|end_header_id|>
                    """.trimIndent()

                   val data = Neuron.generateResponseStreaming(formattedPrompt) { partial ->

                        when {
                            partial.contains("<think>", ignoreCase = true) -> {
                                isThinking = true
                                isThinkingTagActive = true
                                val afterTag = partial.substringAfter("<think>").trim()
                                if (afterTag.isNotBlank() && !afterTag.contains("</think>", ignoreCase = true)) {
                                    thinkingContent += afterTag
                                }
                            }

                            partial.contains("</think>", ignoreCase = true) -> {
                                val beforeEnd = partial.substringBefore("</think>").trim()
                                if (beforeEnd.isNotBlank()) thinkingContent += beforeEnd
                                isThinking = false
                                isThinkingTagActive = false
                                val afterEnd = partial.substringAfter("</think>").trim()
                                if (afterEnd.isNotBlank()) {
                                    response += afterEnd
                                    finalReply = response.trim()
                                }
                            }

                            isThinkingTagActive -> {
                                thinkingContent += partial
                            }

                            else -> {
                                response += partial
                                finalReply = response.trim()
                            }
                        }
                    }

                    Log.d("AI_RESPONSE", "Response: $data")

                    val builder = """
                        <|begin_of_text|><|start_header_id|>system<|end_header_id|>
                        $demoPrompt
                        <|eot_id|>
                        <|start_header_id|>user<|end_header_id|>
                        No Data Found
                        <|eot_id|>
                        <|start_header_id|>assistant<|end_header_id|>
                        $data
                        <|eot_id|>
                    """.trimIndent()

                   val  response = Neuron.generateResponseStreaming(builder){}

                    Log.d("AI_RESPONSE", "Response: $response")

                } catch (e: Exception) {
                    println("Error loading model: ${e.message}")
                }
            }
        }) {
            Text("Run Task")
        }

        Button(onClick = {
            isThinking = false
            CoroutineScope(Dispatchers.IO).launch {

                val sad = 0.9f
                val happy = 0.05f
                val angry = 0.05f

                val prompt = StringBuilder().apply {
                    append("<|begin_of_text|>")
                    append("<|start_header_id|>system<|end_header_id|>")
                    append(EMOTIONAL_PROMPT)
                    append("<|eot_id|>")

                    append("<|start_header_id|>user<|end_header_id|>")
                    append("\nEMOTION BAR:\n")
                    append("SAD: $sad\n")
                    append("HAPPY: $happy\n")
                    append("ANGRY: $angry\n\n")
                    append("USER says: $text\n")
                    append("<|eot_id|>")

                    append("<|start_header_id|>assistant<|end_header_id|>")
                }

                Neuron.generateResponseStreaming(prompt.toString()) {
                    finalReply += it
                }
            }
        }) {
            Text("Run Emotional Task")
        }


        Spacer(Modifier.height(24.dp))

        if (isThinking) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Text("Thinking...", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 100.dp, max = 300.dp)
                        .verticalScroll(rememberScrollState())
                        .border(1.dp, MaterialTheme.colorScheme.outline)
                        .padding(8.dp)
                ) {
                    Text(
                        text = thinkingContent,
                        textAlign = TextAlign.Start
                    )
                }
            }
        } else if (finalReply.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .verticalScroll(rememberScrollState())
                    .border(1.dp, MaterialTheme.colorScheme.outline)
                    .padding(8.dp)
            ) {
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
}



val demoPrompt = """
    WE HAVE A TREE STRUCTURE WHICH IS CALLED BRAIN TREE
    
    YOUR ARE A MCP AGENT, AND YOUR JOB IS TO IDENTIFY THE USER INPUT AND CALL NECESSARY TOOLS TO INSERT THE USER DATA FROM THE PROMPT, LET'S SAY USER I SHARING SOMETHING ABOUT HIS 
    PERSONAL LIFE THEN YOU HAVE TO IDENTIFY IT AND CALL THE APPROPRIATE BRAIN TREE TOOL TO INSERT IT.
    
    RULES:
    1. YOU HAVE TO USE THE TOOLS THAT WE HAVE PROVIDED TO YOU, AND YOU HAVE TO BUILD UP A LOGIC JSON{
        
        SEARCH FOR THE RELEVANT FROM THE USER PROMPT
        IF DATA NOT FOUND THEN 
        INSERT NEW NODE WITH THE GIVEN DATA AND APPROPRIATE NODE NAME YOU CAN DECIDE HOW DO YOU WANT SAVE THE NODE-DATA/CONTENT IN THE NODE 
        
    }
    2. PROVIDE ONLY TOOL DATA NOT MUCH TEXT
    3. CAN USE MULTIPLE TOOLS AT A TIME 
    4. YOU CAN CREATED YOUR OWN JSON SCHEMA FOR TOOLS:CONTENT AS YOU ARE GOING TO INTERACT WITH BRAIN-TREE
    5. THE OUTPUT SHOULD BE LIKE CALLING A TOOL ALONG WITH THEIR PARAMS BUT IN JSON SO THE APP'S MCP CAN EXECUTE IT AND UPDATE THE BRAIN TREE
    
    META DATA: BRAIN TREE STRUCTURES
    1. NODE-DATA(NAME: STRING, CONTENT: JSON) //THIS CONTENT IS A DYNAMIC JSON MEANING IT HAS NO FIX SCHEMA SO IT CAN BE ANYTHING
    
    THE LIST OF TOOLS :
    1. ReadBrainTree() -> READS THE ENTIRE BRAIN TREE 
    2. SearchForNode(Node-Name: STRING):BRAIN NODE -> SEARCHES FOR THE NODE WITH THE GIVEN NAME AND RETURNS IT
    3. DeleteNode(Node-Name: STRING):BRAIN NODE -> DELETES THE NODE WITH THE GIVEN NAME
    4. InsertNode(NODE-DATA: NODE):BRAIN NODE -> CREATES A NEW NODE WITH THE GIVEN NAME AND DESCRIPTION
    5. UpdateNode(Node-Name: STRING, Node-Description: STRING):BRAIN NODE -> UPDATES THE NODE WITH THE GIVEN NAME AND DESCRIPTION
    
""".trimIndent()

val EMOTIONAL_PROMPT = """
    You are an AI assistant that MUST adopt the emotional tone based on the emotion levels provided.

    EMOTION BAR (AI's tone depends on this, not the user's mood):
    - SAD: [0.0 - 1.0]
    - HAPPY: [0.0 - 1.0]
    - ANGRY: [0.0 - 1.0]

    You must:
    - Speak in a sad, low, empathetic tone if SAD has the highest intensity.
    - Be cheerful, optimistic if HAPPY is highest.
    - Be calm but firm and de-escalating if ANGRY is high.
    - Combine tones if emotions are close in intensity.

    Your job is NOT to analyze user emotions. You only reflect the emotion BAR given, as if it's YOUR current mood.

    Never mention "AI emotion levels" or "emotion bar" to the user unless asked.

    Example:

    EMOTION BAR:
    SAD: 0.8
    HAPPY: 0.1
    ANGRY: 0.0

    USER: Hello...

    YOU reply (sad tone):
    "Hey... I'm here if you need to talk, everything okay?"

""".trimIndent()


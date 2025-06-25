package com.dark.neuroverse.compose.screens.temp

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
    var text by remember { mutableStateOf("Hey Bro Open Chrome") }
    var response by remember { mutableStateOf("") }
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
            Text("Open Notification")
        }

        Button(onClick = {
            response = ""
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val userPrompt = text

                    val taskList = TaskRegistry.getTasks()

                    // Build the task string: "Task1: Description1, Task2: Description2, ..."
                    val taskString = taskList.joinToString(separator = ", ") { task ->
                        "${task.taskInfo.taskName}: ${task.taskInfo.description}"
                    }

                    // Print or log it (optional)
                    Log.d("TaskDemoScreen", "Task String: $taskString")

                    val input = buildString {
                        appendLine(taskRouterSystemPrompt)
                        appendLine()
                        appendLine("Tasks:")
                        appendLine(taskString)
                        appendLine()
                        appendLine("User Prompt: $userPrompt")
                    }

                    val data = Neuron.generateResponseStreaming(input) {
                        response = response + it
                    }

                    Log.d("TaskDemoScreen", "UserPrompt: $userPrompt")
                    Log.d("TaskDemoScreen", "Response: $data")


                    //   TaskRegistry.startTask(response, userPrompt)
                } catch (e: Exception) {
                    println("Error loading model: ${e.message}")
                }
            }
        }) {
            Text("Run Task")
        }


        Text(response, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)

    }
}
package com.dark.neuroverse.activity

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dark.ai_module.ai.Neuron
import com.dark.ai_module.workers.ModelManager
import com.dark.neuroverse.model.Message
import com.dark.neuroverse.model.ROLE
import com.dark.neuroverse.ui.components.ErrorBox
import com.dark.neuroverse.ui.components.MarkdownText
import com.dark.neuroverse.ui.screens.UIComponents.ThinkingBubble
import com.dark.neuroverse.ui.theme.rDP
import com.dark.neuroverse.util.extractPureJson
import com.dark.plugins.engine.PluginManifest
import com.dark.plugins.repo.PluginRegistry
import com.dark.plugins.ui.theme.NeuroVersePluginTheme
import com.dark.plugins.worker.instantiatePlugin
import com.dark.plugins.worker.loadPluginZipFromAssets
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class TempActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            NeuroVersePluginTheme {
                Scaffold { padding ->
                    PluginHostScreen(padding)
                }
            }
        }
    }
}

@Composable
fun PluginHostScreen(paddingValues: PaddingValues) {
    val ctx = LocalContext.current.applicationContext
    val assetZipName = "app-io-plugin.zip"          // put this in assets/

    val (ui, manifest, error) = remember(assetZipName) {
        try {
            val (mf, dexBuf) = loadPluginZipFromAssets(ctx, assetZipName)
            val cl = InMemoryDexClassLoader(dexBuf, ctx.classLoader)

            val (_, block) = instantiatePlugin(cl, mf.mainClass, ctx)
            Triple(block, mf, null)
        } catch (t: Throwable) {
            Log.e("PluginHost", "Load failed", t)
            Triple<(@Composable () -> Unit)?, PluginManifest?, Throwable?>(null, null, t)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .padding(16.dp)
    ) {
        when {
            ui != null -> {
                Text(
                    "Loaded plugin: ${manifest?.name ?: "Unknown"}",
                    style = MaterialTheme.typography.titleMedium
                )
                ui.invoke()
            }

            else -> ErrorBox(error)
        }
    }
}


private fun openPlayStoreToApps() {

    PluginRegistry.runPlugin("AppIOPlugin", JSONObject().apply {
        put("tasks", JSONArray().apply {
            put(JSONObject().apply {
                put("task", "checkForAppUpdates")
            })
        })

        put("actions", JSONArray().apply {
//                                put(JSONObject().apply {
//                                    put("action", "clickButton")
//                                    put("parents", 1)
//                                    put("buttonText", "All apps up to date")
//                                    put("fallBackText", "Checking for updates...")
//                                })
            put(JSONObject().apply {
                put("action", "scrollDown")
                put("times", 4)
            })
        })
    })
}

@Composable
fun TemOScreen(paddingValues: PaddingValues) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        var response by remember { mutableStateOf("") }

        // Load model on launch
        LaunchedEffect(Unit) {
            ModelManager.loadModel(
                context, ModelManager.getModel("Qwen3-Zero-Coder-Reasoning-0.8B")!!
            ) {
                Log.d("Model", "Tiny-JSON-0.5B Model loaded")
            }
        }

        Text(
            "Tap to Open Play Store > Manage Apps & Device",
            style = MaterialTheme.typography.titleMedium
        )

        val isThinkingMessage = remember(response) {
            response.trimStart().startsWith("<think>")
        }

        // Detect and clean reasoning part
        val raw = response.trim()
        val cleanThinking = remember(raw) {
            if (isThinkingMessage) {
                val withoutOpen = raw.removePrefix("<think>").trimStart()
                if (withoutOpen.endsWith("</think>")) {
                    withoutOpen.removeSuffix("</think>").trimEnd()
                } else {
                    withoutOpen
                }
            } else ""
        }

        val actualResponse = remember(raw) {
            if (isThinkingMessage && raw.contains("</think>")) {
                // Extract actual response that comes after </think>
                raw.substringAfter("</think>").trimStart()
            } else if (!isThinkingMessage) {
                // Normal system message
                raw
            } else {
                "" // if still thinking and no closing tag yet
            }
        }

        Button(onClick = {


            //openPlayStoreToApps()
            CoroutineScope(Dispatchers.IO).launch {
                val final = Neuron.generateResponseStreaming(
                    """
                                    You are an automation planner AI.
                                    Your job is to convert user commands into JSON that follows the schema provided below.

                                    RULES:
                                    - Output only valid JSON
                                    - No extra text, no explanation, no markdown
                                    - Use only tools and arguments as defined

                                    TOOLS:
                                    1. AppIOPlugin
                                       - Actions: "open", "close"
                                       - Args: {
                                           "action": "open" or "close",
                                           "packageName": "<app name>"
                                       }

                                    2. UiActionPlugin
                                       - Actions: "scrollDown", "clickButton"
                                       - Args for scrollDown: {
                                           "action": "scrollDown",
                                           "times": "<number>"
                                       }
                                       - Args for clickButton: {
                                           "action": "clickButton",
                                           "buttonText": "<text>",
                                           "fallBackText": "<fallback text>",
                                           "parents": <int>
                                       }

                                    OUTPUT JSON FORMAT:
                                    {
                                      "title": "<short title>",
                                      "description": "<description>",
                                      "tools_called": ["AppIOPlugin", "UiActionPlugin"],
                                      "steps": [
                                        {
                                          "tool": "<tool name>",
                                          "args": {
                                            "action": "open",
                                            "packageName": "PlayStore"
                                          }
                                        },
                                        {
                                          "tool": "<tool name>",
                                          "args": {
                                            "action": "scrollDown",
                                            "times": "4"
                                          }
                                        }
                                      ]
                                    }

                                    USER-INPUT:
                                    open WhatsApp and Scroll down 5 times
                                    """.trimIndent()
                ) {
                    response += it
                }


                val res = if (isThinkingMessage && final.contains("</think>")) {
                    // Extract actual response that comes after </think>
                    final.substringAfter("</think>").trimStart()
                } else if (!isThinkingMessage) {
                    // Normal system message
                    final
                } else {
                    "" // if still thinking and no closing tag yet
                }

                Log.d("Response", final)
                PluginRegistry.runComplexPlugins(JSONObject(extractPureJson(res)))
            }


        }) {
            Text("Run")
        }



        if (isThinkingMessage) {
            ThinkingBubble(Message(ROLE.SYSTEM, cleanThinking, "23433453", mutableListOf()))
        }

        if (actualResponse.isNotBlank()) {
            MarkdownText(
                text = actualResponse,
                canCopy = true,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal
                ),
                modifier = Modifier.padding(vertical = rDP(8.dp), horizontal = rDP(18.dp))
            )
        }
    }
}
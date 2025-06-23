package com.dark.neuroverse.compose.screens.temp

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.activities.BrainViewerActivity
import com.dark.userdata.getOrCreateHardwareBackedAesKey
import com.dark.userdata.memoryMapFile
import com.dark.userdata.neuron_tree.NeuronNode
import com.dark.userdata.neuron_tree.NeuronTree
import com.dark.userdata.saveEncryptedTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import kotlin.system.measureTimeMillis

@Composable
fun NeuronTreeScreen(paddingValues: PaddingValues) {
    var addTime by remember { mutableStateOf<Long?>(null) }
    var saveTime by remember { mutableStateOf<Long?>(null) }
    var loadTime by remember { mutableStateOf<Long?>(null) }
    var currentProgress by remember { mutableStateOf("Idle") }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val brainFile = File(context.filesDir, "secure_brain.brain")
    val keyAlias = "brain_key"

    Column(Modifier.padding(paddingValues)) {

        Button(onClick = {
            scope.launch(Dispatchers.Default) {
                currentProgress = "Generating NeuronTree..."

                val key = getOrCreateHardwareBackedAesKey(keyAlias)

                val root = NeuronNode("root", "Root Data")
                val tree = NeuronTree(root)

                val totalNodes = 100_00

                val add = measureTimeMillis {
                    repeat(totalNodes) { index ->
                        if (index % 500 == 0) {
                            currentProgress = "Adding Node $index"
                        }
                        if (index == 20) {
                            val node = NeuronNode(
                                "nodeU",
                                "Unique Node"
                            )
                            tree.addChild("root", node)
                        }
                        val node = NeuronNode(
                            "node-$index",
                            "Data $index - Large payload simulating real-world data."
                        )
                        tree.addChild("root", node)
                    }
                }
                addTime = add

                currentProgress = "Encrypting & Saving..."
                val save = measureTimeMillis {
                    saveEncryptedTree(tree, brainFile, key)
                }
                saveTime = save

                currentProgress = "Memory Mapping File..."
                val load = measureTimeMillis {
                    memoryMapFile(brainFile)
                }
                loadTime = load

                currentProgress = "Completed"
            }
        }) {
            Text("Start NeuronTree Test")
        }

        Spacer(Modifier.height(16.dp))

        Text("Progress: $currentProgress")
        Text("Time to add nodes: ${addTime ?: "-"} ms")
        Text("Time to save encrypted tree: ${saveTime ?: "-"} ms")
        Text("Time to memory map file: ${loadTime ?: "-"} ms")

        Button(onClick = {
            val intent = Intent(context, BrainViewerActivity::class.java)
            intent.putExtra("brain_file_path", brainFile.absolutePath)
            intent.putExtra("brain_key_alias", keyAlias)
            context.startActivity(intent)
        }) {
            Text("View Brain")
        }
    }
}

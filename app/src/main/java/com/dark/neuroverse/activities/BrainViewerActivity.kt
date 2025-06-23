package com.dark.neuroverse.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.dark.userdata.collectAllNodes
import com.dark.userdata.getBrainFilePath
import com.dark.userdata.getOrCreateHardwareBackedAesKey
import com.dark.userdata.loadEncryptedTree
import com.dark.userdata.neuron_tree.NeuronTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import java.io.File

class BrainViewerActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val filePath = intent.getStringExtra("brain_file_path")
        if (filePath.isNullOrEmpty()) {
            Toast.makeText(this, "Invalid file provided", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            NeuroVerseTheme {
                Scaffold(topBar = {
                    TopAppBar(title = { Text("Brain Viewer") })
                }) { padding ->
                    BrainScreen(padding, filePath)
                }
            }
        }
    }
}

@Composable
fun BrainScreen(paddingValues: PaddingValues, filePath: String) {

    val context = LocalContext.current
    var nodeList by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }

    val key = getOrCreateHardwareBackedAesKey()

    LaunchedEffect(Unit) {
        val nodeTree: NeuronTree? = loadEncryptedTree(getBrainFilePath(context), key)

        CoroutineScope(Dispatchers.IO).async {
            nodeTree?.collectAllNodes()?.forEach {
                nodeList += Pair(it.id, it.data.toString())
                Log.d("NeuronTree", "ID=${it.id}, Data=${it.data}")
            }
        }
    }

    Column(
        modifier = Modifier
            .padding(paddingValues)
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Opened File:", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(filePath, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        if (nodeList.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No data found or failed to decrypt.")
            }
        } else {
            LazyColumn {
                items(nodeList) { (id, data) ->
                    Card(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(0.dp)
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text("Node ID: $id", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text("Data: $data", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

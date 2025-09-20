package com.dark.neuroverse.activity

import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dark.neuroverse.ui.theme.NeuroVerseTheme
import com.mp.ai_core.EmbeddingManager
import com.mp.ai_core.NativeLib
import com.mp.data_hub_lib.worker.BrainDecoder
import com.mp.data_hub_lib.worker.BrainRoot
import com.mp.data_hub_lib.worker.DataHubWorker
import com.mp.data_hub_lib.worker.Doc
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlin.math.sqrt
import java.io.File

class ScrapActivity : ComponentActivity() {
    private lateinit var dataHubWorker: DataHubWorker
    private val appStatus = MutableStateFlow(DataAppStatus.IDLE)

    // RAG / model related
    private val native = NativeLib() // adjust if NativeLib is an object or needs other init
    private lateinit var embeddingManager: EmbeddingManager
    private var embeddingModelPath = "/storage/emulated/0/Download/ai/all-MiniLM-L6-v2-Q8_0.gguf"
    private var m1 = "/storage/emulated/0/Download/Models/Kodify-Nano-2.0.Q8_0.gguf"

    @OptIn(ExperimentalMaterial3Api::class)
    @RequiresApi(Build.VERSION_CODES.S)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        dataHubWorker = DataHubWorker(applicationContext)
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val zipFile = File(downloadsDir, "ai/protected_embeddings.zip")

        // initialize embeddingManager with the native lib
        embeddingManager = EmbeddingManager(native)

        setContent {
            NeuroVerseTheme {
                val status by appStatus.collectAsState()
                var licenseValid by remember { mutableStateOf(false) }
                var licenseText by remember { mutableStateOf("") }
                var mItems by remember { mutableStateOf(listOf<kotlinx.serialization.json.JsonObject>()) }
                var query by remember { mutableStateOf("") }
                var isProcessing by remember { mutableStateOf(false) }
                var errorMessage by remember { mutableStateOf("") }
                var brainRoot by remember { mutableStateOf<BrainRoot?>(null) }
                var searchResults by remember { mutableStateOf<List<Doc>>(emptyList()) }
                var currentModelName by remember { mutableStateOf<String?>(null) }
                var response by remember { mutableStateOf("") }
                var stats by remember { mutableStateOf<GenerationStats?>(null) }

                LaunchedEffect(Unit) {
                    appStatus.value = DataAppStatus.LOADING
                    dataHubWorker.loadPack(zipFile.absolutePath, com.dark.neuroverse.BuildConfig.ALIAS) { success, modelName ->
                        if (success && modelName != null) {
                            currentModelName = modelName

                            lifecycleScope.launch {
                                try {
                                    val mJson = dataHubWorker.dataNativeLib.getEntity("D")
                                    val root = BrainDecoder.loadBrain(mJson)
                                    if (root != null) {
                                        brainRoot = root
                                        Log.d("BrainDecoder", "Brain loaded successfully")
                                    } else {
                                        Log.e("BrainDecoder", "Failed to load brain")
                                    }

                                    // Try to parse array to list (silent if fails)
                                    try {
                                        val arr = Json.parseToJsonElement(mJson).jsonArray
                                        val temp = mutableListOf<JsonObject>()
                                        arr.forEach { if (it is JsonObject) temp.add(it) }
                                        mItems = temp
                                    } catch (_: Exception) {
                                    }

                                    appStatus.value = DataAppStatus.READY
                                } catch (e: Exception) {
                                    errorMessage = "Error loading datapack: ${e.message}"
                                    appStatus.value = DataAppStatus.ERROR
                                }
                            }
                        } else {
                            errorMessage = "Failed to install datapack."
                            appStatus.value = DataAppStatus.ERROR
                        }
                    }
                }

                fun performLocalSearch(queryEmbedding: FloatArray) {
                    if (brainRoot != null) {
                        searchResults = BrainDecoder.search(queryEmbedding)
                        Log.d("BrainDecoder", "Search results: $searchResults")
                    } else {
                        errorMessage = "Brain not loaded"
                    }
                }

                Scaffold(
                    topBar = { TopAppBar(title = { Text("NeuroVerse (Scrap RAG)") }) },
                    floatingActionButton = {
                        FloatingActionButton(onClick = {
                            // quick-search demo embedding
                            val queryEmbedding = FloatArray(384) { 0.1f }
                            performLocalSearch(queryEmbedding)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Search")
                        }
                    }
                ) { padding ->
                    Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
                        when (status) {
                            DataAppStatus.LOADING -> {
                                CenteredLoading("Loading datapack...")
                            }
                            DataAppStatus.ERROR -> {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                                    Text(text = "Error: $errorMessage", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                            DataAppStatus.READY -> {
                                OutlinedTextField(
                                    value = query,
                                    onValueChange = { query = it },
                                    label = { Text("Enter query for RAG") },
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = !isProcessing
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                Button(
                                    onClick = {
                                        lifecycleScope.launch {
                                            performRAG(
                                                query = query,
                                                onUpdate = { response = it },
                                                onSearchResults = { searchResults = it },
                                                onDone = { stats = it },
                                                onError = { errorMessage = it },
                                                onProcessingChange = { isProcessing = it }
                                            )
                                        }
                                    },
                                    enabled = !isProcessing && query.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isProcessing) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.size(8.dp))
                                        Text("Processing...")
                                    } else {
                                        Text("Search & Generate")
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (searchResults.isNotEmpty()) {
                                        items(searchResults) { doc ->
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text(text = "Similarity: ${String.format("%.3f", doc.similarity)}", style = MaterialTheme.typography.labelSmall)
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text(text = doc.text, style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                        }
                                    }

                                    if (response.isNotBlank()) {
                                        item {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Text(response, modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
                                            }
                                        }
                                    }

                                    stats?.let { s ->
                                        item {
                                            Card(modifier = Modifier.fillMaxWidth()) {
                                                Column(modifier = Modifier.padding(12.dp)) {
                                                    Text("Generation Stats:")
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Text("Tokens: ${s.tokenCount}")
                                                    Text("Time: ${s.totalTime}ms")
                                                    Text("Speed: ${String.format("%.2f", s.tokensPerSecond)} tokens/sec")
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            else -> {
                                CenteredLoading("Initializing...")
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CenteredLoading(text: String) {

            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text(text)
            }

    }

    private suspend fun performRAG(
        query: String,
        onUpdate: (String) -> Unit,
        onSearchResults: (List<Doc>) -> Unit,
        onDone: (GenerationStats) -> Unit,
        onError: (String) -> Unit,
        onProcessingChange: (Boolean) -> Unit
    ) = withContext(Dispatchers.IO) {
        onProcessingChange(true)
        onError("")

        try {
            if (query.isBlank()) {
                onError("Please enter a valid query")
                return@withContext
            }

            val startTime = System.currentTimeMillis()

            // initialize embedding model (safe to call multiple times, adjust if heavy)
            val modelInitResult = embeddingManager.initializeEmbedding(modelPath = embeddingModelPath)
            modelInitResult.onFailure {
                onError("Error initializing embedding model: ${it.message}")
                Log.e("ScrapActivity", "Embedding init failed: ${it.message}")
                return@withContext
            }

            // get embedding
            val queryEmbedding = embeddingManager.getEmbedding(query).getOrElse {
                onError("Error embedding query: ${it.message}")
                Log.e("ScrapActivity", "Error embedding query: ${it.message}")
                return@withContext
            }

            // search local brain
            val topDocs = BrainDecoder.search(queryEmbedding, topK = 5)
            withContext(Dispatchers.Main) {
                onSearchResults(topDocs)
            }

            if (topDocs.isEmpty()) {
                onError("No relevant documents found")
                return@withContext
            }

            val context = topDocs.joinToString("\n\n") { "Context: ${it.text}" }
            val prompt = """Based on the following context, please answer the question.
                Context:
                $context
                Question: $query
                Answer:"""

            // init generation model
            val ok = native.initModel(
                path = m1,
                threads = (Runtime.getRuntime().availableProcessors() - 1).coerceAtLeast(1),
                gpuLayers = 10,
                useMMAP = true,
                useMLOCK = false,
                ctxSize = 4096,
                temp = 0.7f,
                topK = 40,
                topP = 0.9f,
                minP = 0.0f
            )

            if (!ok) {
                onError("Failed to initialize model at $m1")
                return@withContext
            }

            var tokenCount = 0
            var fullResponse = ""

            native.generateStreaming(
                prompt = prompt,
                maxTokens = 512,
                uiScope = CoroutineScope(Dispatchers.Main),
                onStart = {
                    onUpdate("")
                },
                onGenerate = { token ->
                    fullResponse += token
                    tokenCount++
                    onUpdate(fullResponse)
                },
                onError = { err ->
                    onError("Generation error: $err")
                    Log.e("ScrapActivity", "Generation error: $err")
                },
                onDone = {
                    val totalTime = System.currentTimeMillis() - startTime
                    val tokensPerSecond = if (totalTime > 0) tokenCount * 1000f / totalTime else 0f
                    onDone(GenerationStats(tokenCount, totalTime, tokensPerSecond))
                }
            )

        } catch (e: Exception) {
            Log.e("ScrapActivity", "RAG error: ${e.message}", e)
            onError("Unexpected error: ${e.message}")
        } finally {
            onProcessingChange(false)
        }
    }

    data class GenerationStats(val tokenCount: Int, val totalTime: Long, val tokensPerSecond: Float)

    enum class DataAppStatus {
        IDLE,
        LOADING,
        READY,
        ERROR
    }
}

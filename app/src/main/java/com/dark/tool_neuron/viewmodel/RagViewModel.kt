package com.dark.tool_neuron.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.models.table_schema.InstalledRag
import com.dark.tool_neuron.models.table_schema.RagStatus
import com.dark.tool_neuron.models.vault.ChatInfo
import com.dark.tool_neuron.neuron_example.EmbeddingConfig
import com.dark.tool_neuron.neuron_example.EmbeddingProvider
import com.dark.tool_neuron.neuron_example.GraphSettings
import com.dark.tool_neuron.neuron_example.NeuronGraph
import com.dark.tool_neuron.neuron_example.QueryResult
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.RagRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

// Data class for displaying RAG query results in UI
data class RagQueryDisplayResult(
    val ragName: String,
    val content: String,
    val score: Float,
    val nodeId: String
)

@HiltViewModel
class RagViewModel @Inject constructor(
    private val ragRepository: RagRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val chatRepository: ChatRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    // UI State
    private val _showRagOverlay = MutableStateFlow(false)
    val showRagOverlay: StateFlow<Boolean> = _showRagOverlay

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _selectedRag = MutableStateFlow<InstalledRag?>(null)
    val selectedRag: StateFlow<InstalledRag?> = _selectedRag

    private val _embeddingStatus = MutableStateFlow("Not Initialized")
    val embeddingStatus: StateFlow<String> = _embeddingStatus

    private val _isEmbeddingInitialized = MutableStateFlow(false)
    val isEmbeddingInitialized: StateFlow<Boolean> = _isEmbeddingInitialized

    // Chat list for creating RAG from chats
    private val _availableChats = MutableStateFlow<List<ChatInfo>>(emptyList())
    val availableChats: StateFlow<List<ChatInfo>> = _availableChats

    // RAG Lists
    val installedRags = ragRepository.getAllRags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val loadedRags = ragRepository.getLoadedRags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val enabledRags = ragRepository.getEnabledRags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Counts
    private val _installedCount = MutableStateFlow(0)
    val installedCount: StateFlow<Int> = _installedCount

    private val _loadedCount = MutableStateFlow(0)
    val loadedCount: StateFlow<Int> = _loadedCount

    // Embedding state
    val isEmbeddingReady: Boolean get() = embeddingProvider.isInitialized()

    // RAG enabled for chat
    private val _isRagEnabledForChat = MutableStateFlow(false)
    val isRagEnabledForChat: StateFlow<Boolean> = _isRagEnabledForChat

    // Last RAG query results for display
    private val _lastRagResults = MutableStateFlow<List<RagQueryDisplayResult>>(emptyList())
    val lastRagResults: StateFlow<List<RagQueryDisplayResult>> = _lastRagResults

    init {
        // Sync database state with in-memory state on startup
        // Since loadedGraphs is empty on app restart, mark all RAGs as unloaded
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.syncLoadedStateOnStartup()
        }

        refreshCounts()
        _isEmbeddingInitialized.value = embeddingProvider.isInitialized()
        if (embeddingProvider.isInitialized()) {
            _embeddingStatus.value = "Ready (dim: ${embeddingProvider.getDimension()})"
        }
    }

    private fun refreshCounts() {
        viewModelScope.launch(Dispatchers.IO) {
            _installedCount.value = ragRepository.getRagCount()
            _loadedCount.value = ragRepository.getLoadedRagCount()
        }
    }

    // ==================== UI Controls ====================

    fun showRagOverlay() {
        _showRagOverlay.value = true
        refreshCounts()
    }

    fun hideRagOverlay() {
        _showRagOverlay.value = false
        _selectedRag.value = null
    }

    fun selectRag(rag: InstalledRag) {
        _selectedRag.value = rag
    }

    fun clearSelection() {
        _selectedRag.value = null
    }

    fun clearError() {
        _error.value = null
    }

    // ==================== RAG Operations ====================

    fun toggleRagEnabled(ragId: String, isEnabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.updateRagEnabled(ragId, isEnabled)
        }
    }

    fun loadRag(ragId: String, password: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            try {
                ragRepository.updateRagStatus(ragId, RagStatus.LOADING)

                val graph = NeuronGraph(embeddingProvider, GraphSettings.DEFAULT)
                val result = ragRepository.loadGraph(ragId, graph, password)

                if (result.isSuccess) {
                    _loadedCount.value = ragRepository.getLoadedRagCount()
                } else {
                    ragRepository.updateRagStatus(ragId, RagStatus.ERROR)
                    _error.value = result.exceptionOrNull()?.message ?: "Failed to load RAG"
                }
            } catch (e: Exception) {
                ragRepository.updateRagStatus(ragId, RagStatus.ERROR)
                _error.value = e.message
            }

            _isLoading.value = false
        }
    }

    fun unloadRag(ragId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.unloadGraph(ragId)
            _loadedCount.value = ragRepository.getLoadedRagCount()
        }
    }

    fun unloadAllRags() {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.unloadAllRags()
            _loadedCount.value = 0
        }
    }

    fun deleteRag(ragId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            ragRepository.deleteRag(ragId)
            refreshCounts()
            if (_selectedRag.value?.id == ragId) {
                _selectedRag.value = null
            }
        }
    }

    fun installRagFromUri(uri: Uri, name: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            val result = ragRepository.installRagFromUri(uri, name)
            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to install RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
        }
    }

    // ==================== Query Operations ====================

    suspend fun queryRags(query: String, topK: Int = 5): List<Pair<InstalledRag, List<QueryResult>>> {
        return ragRepository.queryAllLoadedGraphs(query, topK)
    }

    suspend fun queryAndFormat(query: String, topK: Int = 3): String {
        val results = queryRags(query, topK)
        if (results.isEmpty()) return ""

        val contextBuilder = StringBuilder()
        contextBuilder.append("### Relevant Context from Knowledge Base:\n\n")

        for ((rag, queryResults) in results) {
            if (queryResults.isNotEmpty()) {
                contextBuilder.append("**From ${rag.name}:**\n")
                for (result in queryResults) {
                    val score = (result.score * 100).toInt()
                    contextBuilder.append("- [$score% match] ${result.node.content.take(500)}\n")
                }
                contextBuilder.append("\n")
            }
        }

        return contextBuilder.toString()
    }

    // ==================== Creation Operations ====================

    fun createRagFromText(
        name: String,
        description: String,
        text: String,
        domain: String = "general",
        tags: List<String> = emptyList(),
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            if (!embeddingProvider.isInitialized()) {
                _error.value = "Embedding provider not initialized"
                _isLoading.value = false
                onComplete(Result.failure(Exception("Embedding provider not initialized")))
                return@launch
            }

            val graph = NeuronGraph(embeddingProvider, GraphSettings.DEFAULT)
            val result = ragRepository.createRagFromText(name, description, text, graph, domain, tags)

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

    fun createRagFromChat(
        name: String,
        description: String,
        chatId: String,
        messages: List<com.dark.tool_neuron.models.messages.Messages>,
        domain: String = "general",
        tags: List<String> = emptyList(),
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            if (!embeddingProvider.isInitialized()) {
                _error.value = "Embedding provider not initialized"
                _isLoading.value = false
                onComplete(Result.failure(Exception("Embedding provider not initialized")))
                return@launch
            }

            val graph = NeuronGraph(embeddingProvider, GraphSettings.DEFAULT)
            val result = ragRepository.createRagFromChat(name, description, chatId, messages, graph, domain, tags)

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

    fun createRagFromFile(
        name: String,
        description: String,
        fileUri: Uri,
        domain: String = "general",
        tags: List<String> = emptyList(),
        onComplete: (Result<InstalledRag>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null

            if (!embeddingProvider.isInitialized()) {
                _error.value = "Embedding provider not initialized"
                _isLoading.value = false
                onComplete(Result.failure(Exception("Embedding provider not initialized")))
                return@launch
            }

            val graph = NeuronGraph(embeddingProvider, GraphSettings.DEFAULT)
            val result = ragRepository.createRagFromFile(name, description, fileUri, graph, domain, tags)

            if (result.isFailure) {
                _error.value = result.exceptionOrNull()?.message ?: "Failed to create RAG"
            } else {
                refreshCounts()
            }

            _isLoading.value = false
            onComplete(result)
        }
    }

    // ==================== Embedding Initialization ====================

    fun initializeEmbedding(modelPath: String, tokenizerPath: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _embeddingStatus.value = "Initializing..."
            _isLoading.value = true

            val config = EmbeddingConfig(
                modelPath = modelPath,
                tokenizerPath = tokenizerPath,
                modelName = "sentence-embedding"
            )

            val result = embeddingProvider.initialize(config)
            if (result.isSuccess) {
                _isEmbeddingInitialized.value = true
                _embeddingStatus.value = "Ready (dim: ${embeddingProvider.getDimension()})"
            } else {
                _isEmbeddingInitialized.value = false
                _embeddingStatus.value = "Error: ${result.exceptionOrNull()?.message}"
                _error.value = result.exceptionOrNull()?.message
            }

            _isLoading.value = false
        }
    }

    fun initializeEmbeddingFromAssets() {
        viewModelScope.launch(Dispatchers.IO) {
            _embeddingStatus.value = "Checking models..."
            _isLoading.value = true

            // Check for embedding model in app's files directory
            val modelsDir = File(context.filesDir, "models/embedding")
            val modelFile = File(modelsDir, "model.onnx")
            val tokenizerFile = File(modelsDir, "tokenizer.json")

            if (modelFile.exists() && tokenizerFile.exists()) {
                val config = EmbeddingConfig(
                    modelPath = modelFile.absolutePath,
                    tokenizerPath = tokenizerFile.absolutePath,
                    modelName = "sentence-embedding"
                )

                val result = embeddingProvider.initialize(config)
                if (result.isSuccess) {
                    _isEmbeddingInitialized.value = true
                    _embeddingStatus.value = "Ready (dim: ${embeddingProvider.getDimension()})"
                } else {
                    _isEmbeddingInitialized.value = false
                    _embeddingStatus.value = "Error: ${result.exceptionOrNull()?.message}"
                    _error.value = result.exceptionOrNull()?.message
                }
            } else {
                _embeddingStatus.value = "Model not found - Please install embedding model"
                _error.value = "Embedding model files not found. Please install a sentence embedding model in the models/embedding directory."
            }

            _isLoading.value = false
        }
    }

    // ==================== Chat Operations ====================

    fun loadAvailableChats() {
        viewModelScope.launch(Dispatchers.IO) {
            val result = chatRepository.getAllChats()
            if (result.isSuccess) {
                _availableChats.value = result.getOrDefault(emptyList())
            }
        }
    }

    suspend fun getChatMessages(chatId: String): List<com.dark.tool_neuron.models.messages.Messages> {
        return chatRepository.getMessages(chatId).getOrDefault(emptyList())
    }

    // ==================== RAG for Chat Toggle ====================

    fun toggleRagForChat(enabled: Boolean) {
        _isRagEnabledForChat.value = enabled
    }

    // ==================== Query with Display Results ====================

    suspend fun queryAndStoreResults(query: String, topK: Int = 5): String {
        // Query all loaded RAGs - no need to check isRagEnabledForChat here
        // The caller (HomeScreen) already checks if loadedRags is not empty
        val results = queryRags(query, topK)
        if (results.isEmpty()) {
            _lastRagResults.value = emptyList()
            return ""
        }

        // Store results for UI display
        val displayResults = mutableListOf<RagQueryDisplayResult>()
        val contextBuilder = StringBuilder()
        contextBuilder.append("### Relevant Context from Knowledge Base:\n\n")

        for ((rag, queryResults) in results) {
            if (queryResults.isNotEmpty()) {
                contextBuilder.append("**From ${rag.name}:**\n")
                for (result in queryResults) {
                    val score = result.score
                    val scorePercent = (score * 100).toInt()
                    val contentPreview = result.node.content.take(500)
                    contextBuilder.append("- [$scorePercent% match] $contentPreview\n")

                    displayResults.add(
                        RagQueryDisplayResult(
                            ragName = rag.name,
                            content = result.node.content,
                            score = score,
                            nodeId = result.node.id
                        )
                    )
                }
                contextBuilder.append("\n")
            }
        }

        _lastRagResults.value = displayResults.sortedByDescending { it.score }
        return contextBuilder.toString()
    }

    fun clearRagResults() {
        _lastRagResults.value = emptyList()
    }

    // ==================== File Content Reading ====================

    suspend fun readFileContent(uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader().readText()
            }
        } catch (e: Exception) {
            _error.value = "Failed to read file: ${e.message}"
            null
        }
    }
}
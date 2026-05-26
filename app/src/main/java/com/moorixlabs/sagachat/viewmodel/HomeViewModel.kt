package com.moorixlabs.sagachat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moorixlabs.sagachat.model.Chat
import com.moorixlabs.sagachat.model.ChatMessage
import com.moorixlabs.sagachat.model.MemoryMetrics
import com.moorixlabs.sagachat.model.MessageKind
import com.moorixlabs.sagachat.model.ModelInfo
import com.moorixlabs.sagachat.model.TextMetrics
import com.moorixlabs.sagachat.model.enums.ProviderType
import com.moorixlabs.sagachat.data.AppPreferences
import com.moorixlabs.sagachat.repo.ChatRepository
import com.moorixlabs.sagachat.repo.ModelRepository
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.service.inference.InferenceEvent
import com.moorixlabs.sagachat.viewmodel.home_vm.GenerationOutcome
import com.moorixlabs.sagachat.viewmodel.home_vm.GenerationStatus
import com.moorixlabs.sagachat.viewmodel.home_vm.InferenceCoordinator
import com.moorixlabs.sagachat.viewmodel.home_vm.ModelLoadState
import com.moorixlabs.sagachat.viewmodel.home_vm.ModelSessionManager
import com.moorixlabs.sagachat.viewmodel.home_vm.PillState
import com.moorixlabs.sagachat.viewmodel.home_vm.StreamingFragment
import com.moorixlabs.sagachat.viewmodel.home_vm.ToolCallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.launch
import com.moorixlabs.sagachat.util.ChatExporter
import com.moorixlabs.sagachat.util.ChatShareHelper
import com.moorixlabs.sagachat.util.ExportFormat
import com.moorixlabs.sagachat.ui.components.ContextStatsReport
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

private const val ROLE_USER = "user"
private const val ROLE_ASSISTANT = "assistant"
private val WHITESPACE = "\\s+".toRegex()

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val app: Application,
    private val modelRepo: ModelRepository,
    private val chatRepo: ChatRepository,
    private val modelSession: ModelSessionManager,
    private val toolCallCoordinator: ToolCallCoordinator,
    private val inferenceCoordinator: InferenceCoordinator,
    private val appPrefs: AppPreferences,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models

    val chatModels: StateFlow<List<ModelInfo>> = modelRepo.models
        .map { list -> list.filter { it.providerType == ProviderType.GGUF } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val chats: StateFlow<List<Chat>> = chatRepo.chats
    val modelLoadState: StateFlow<ModelLoadState> = modelSession.loadState
    val supportsThinking: StateFlow<Boolean> = modelSession.supportsThinking

    val activeModel: StateFlow<ModelInfo?> = installedModels
        .map { list -> list.firstOrNull { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _streamingFragment = MutableStateFlow<StreamingFragment?>(null)
    val streamingFragment: StateFlow<StreamingFragment?> = _streamingFragment.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _thinkingEnabled = MutableStateFlow(false)
    val thinkingEnabled = _thinkingEnabled.asStateFlow()

    private val _currentChatId = MutableStateFlow<String?>(null)
    val currentChatId: StateFlow<String?> = _currentChatId.asStateFlow()

    private val _loadModelWindow = MutableStateFlow(false)
    val loadModelWindows: StateFlow<Boolean> = _loadModelWindow.asStateFlow()

    private val _lastTextMetrics = MutableStateFlow<TextMetrics?>(null)
    val lastTextMetrics: StateFlow<TextMetrics?> = _lastTextMetrics.asStateFlow()

    private val _lastMemoryMetrics = MutableStateFlow<MemoryMetrics?>(null)
    val lastMemoryMetrics: StateFlow<MemoryMetrics?> = _lastMemoryMetrics.asStateFlow()

    private val _contextUsage = MutableStateFlow(0f)
    val contextUsage: StateFlow<Float> = _contextUsage.asStateFlow()

    val pillState: StateFlow<PillState> = combine(
        InferenceClient.isModelLoaded,
        modelSession.loadState,
        _isGenerating,
        _thinkingEnabled,
        modelSession.supportsThinking,
    ) { isLoaded, loadState, generating, thinkingOn, supportsThink ->
        when {
            generating && thinkingOn && supportsThink -> PillState.Thinking
            generating -> PillState.Generating
            loadState is ModelLoadState.Loading -> PillState.Loading
            loadState is ModelLoadState.Error -> PillState.Error
            isLoaded -> PillState.Loaded
            else -> PillState.Idle
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PillState.Idle)

    val generationStatus: StateFlow<GenerationStatus> = combine(
        InferenceClient.isModelLoaded,
        modelSession.loadState,
        _isGenerating,
        _thinkingEnabled,
        modelSession.supportsThinking,
        activeModel,
        _messages,
        _streamingFragment,
        _contextUsage,
    ) { args ->
        val isLoaded = args[0] as Boolean
        val loadState = args[1] as ModelLoadState
        val generating = args[2] as Boolean
        val thinkingOn = args[3] as Boolean
        val supportsThink = args[4] as Boolean
        val active = args[5] as ModelInfo?
        val msgs = @Suppress("UNCHECKED_CAST") (args[6] as List<ChatMessage>)
        val streaming = args[7] as StreamingFragment?
        val ctxUsage = args[8] as Float

        val modelName = active?.name ?: streaming?.let { "Model" } ?: ""
        when {
            loadState is ModelLoadState.Loading -> GenerationStatus.ModelLoading(modelName.ifBlank { "Model" })
            loadState is ModelLoadState.Error -> GenerationStatus.Error(loadState.message, modelName.ifBlank { null })
            generating -> {
                val words = streaming?.content?.wordCount() ?: 0
                if (thinkingOn && supportsThink) {
                    GenerationStatus.Thinking(modelName.ifBlank { "Model" }, words, ctxUsage)
                } else {
                    GenerationStatus.GeneratingText(modelName.ifBlank { "Model" }, words, ctxUsage)
                }
            }
            !isLoaded && msgs.isEmpty() -> GenerationStatus.Welcome
            !isLoaded -> GenerationStatus.NoModelLoaded
            else -> GenerationStatus.Hidden
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, GenerationStatus.Welcome)

    private var generationJob: Job? = null

    private val _compactionState = MutableStateFlow(CompactionState())
    val compactionState: StateFlow<CompactionState> = _compactionState.asStateFlow()
    private var compactionJob: Job? = null

    private val _contextStatsReport = MutableStateFlow<ContextStatsReport?>(null)
    val contextStatsReport: StateFlow<ContextStatsReport?> = _contextStatsReport.asStateFlow()

    fun openContextStats() {
        val memJson = InferenceClient.getMemoryStatsJson()
        val vtJson  = InferenceClient.getVtCacheStatsJson()
        val nCtx  = runCatching { JSONObject(memJson ?: "{}").optInt("nCtx", 0) }.getOrDefault(0)
        val nUsed = runCatching { JSONObject(memJson ?: "{}").optInt("nUsed", 0) }.getOrDefault(0)
        val pct   = if (nCtx > 0) nUsed.toDouble() / nCtx * 100.0 else _contextUsage.value.toDouble() * 100.0
        val modelMb   = runCatching { JSONObject(memJson ?: "{}").optDouble("modelMb", 0.0) }.getOrDefault(0.0)
        val kvMb      = runCatching { JSONObject(memJson ?: "{}").optDouble("kvMb", 0.0) }.getOrDefault(0.0)
        val rss       = runCatching { JSONObject(memJson ?: "{}").optDouble("rssMb", 0.0) }.getOrDefault(0.0)
        val peakRss   = runCatching { JSONObject(memJson ?: "{}").optDouble("peakRssMb", 0.0) }.getOrDefault(0.0)
        val memAvail  = runCatching { JSONObject(memJson ?: "{}").optDouble("memAvailMb", 0.0) }.getOrDefault(0.0)
        val memTotal  = runCatching { JSONObject(memJson ?: "{}").optDouble("memTotalMb", 0.0) }.getOrDefault(0.0)
        val vtEntries = runCatching { JSONObject(vtJson ?: "{}").optInt("entries", 0) }.getOrDefault(0)
        val vtBytes   = runCatching { JSONObject(vtJson ?: "{}").optLong("bytes", 0L) }.getOrDefault(0L)
        val vtHits    = runCatching { JSONObject(vtJson ?: "{}").optLong("hits", 0L) }.getOrDefault(0L)
        val vtMisses  = runCatching { JSONObject(vtJson ?: "{}").optLong("misses", 0L) }.getOrDefault(0L)
        _contextStatsReport.value = ContextStatsReport(
            nCtx = nCtx, nUsed = nUsed, contextUsagePct = pct,
            modelMb = modelMb, kvCacheMb = kvMb,
            currentRssMb = rss, peakRssMb = peakRss,
            memAvailableMb = memAvail, memTotalMb = memTotal,
            threadMode = appPrefs.threadMode,
            modelLoaded = InferenceClient.isModelLoaded.value,
            vlmLoaded = false, vtCacheInit = vtEntries > 0,
            vtCacheEntries = vtEntries, vtCacheBytes = vtBytes,
            vtCacheHits = vtHits, vtCacheMisses = vtMisses,
            vlmKvCacheInit = false, vlmKvCacheEntries = 0, vlmKvCacheBytes = 0,
            vlmKvCacheHits = 0, vlmKvCacheMisses = 0,
            visionIndexEntries = 0, visionIndexBytes = 0,
        )
    }

    fun dismissContextStats() { _contextStatsReport.value = null }

    init {
        viewModelScope.launch {
            combine(InferenceClient.isModelLoaded, InferenceClient.contextUsage) { loaded, usage ->
                if (loaded) usage else 0f
            }.collect { _contextUsage.value = it }
        }
    }

    fun toggleThinking() {
        _thinkingEnabled.value = !_thinkingEnabled.value
    }

    fun toggleLoadModelWindow() {
        if (_isGenerating.value) {
            _loadModelWindow.value = false
            return
        }
        _loadModelWindow.value = !_loadModelWindow.value
    }

    fun selectChat(chatId: String) {
        _currentChatId.value = chatId
        _messages.value = chatRepo.getMessages(chatId)
    }

    fun createNewChat() {
        _currentChatId.value = null
        _messages.value = emptyList()
    }

    fun deleteChat(chatId: String) {
        chatRepo.deleteChat(chatId)
        if (_currentChatId.value == chatId) createNewChat()
    }

    fun pinChat(chatId: String, pinned: Boolean) {
        chatRepo.pinChat(chatId, pinned)
    }

    fun exportChat(chatId: String, format: ExportFormat) {
        val chat = chatRepo.getChatById(chatId) ?: return
        val messages = chatRepo.getMessages(chatId)
        if (messages.isEmpty()) return
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val body = ChatExporter.format(chat, messages, format)
            val filename = ChatExporter.suggestedFilename(chat, format)
            ChatShareHelper.writeAndShare(
                context = app,
                filename = filename,
                body = body,
                mimeType = format.mimeType,
                title = chat.title,
            )
        }
    }

    fun loadModel(model: ModelInfo) {
        if (_isGenerating.value) return
        viewModelScope.launch { modelSession.load(model) }
    }

    fun unloadModel() {
        if (_isGenerating.value) return
        viewModelScope.launch { modelSession.unload() }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (_isGenerating.value) return
        if (trimmed.isEmpty()) return

        val active = activeModel.value
            ?: chatModels.value.firstOrNull()?.also { modelRepo.setActive(it.id) }
            ?: run {
                _loadModelWindow.value = true
                return
            }

        val chatId = ensureChat(active)
        val userMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_USER,
            content = trimmed,
            timestamp = System.currentTimeMillis(),
            kind = MessageKind.Text,
        )
        chatRepo.addMessage(userMessage)

        val isFirstTurn = chatRepo.getMessages(chatId).count { it.role == ROLE_USER } == 1

        if (InferenceClient.isModelLoaded.value) {
            runGeneration(chatId, isFirstTurn, trimmed)
        } else {
            viewModelScope.launch {
                try {
                    modelSession.load(active)
                } catch (e: Exception) {
                    Log.w("HomeViewModel", "auto-load failed: ${e.message}")
                    return@launch
                }
                if (modelSession.loadState.value is ModelLoadState.Active) {
                    runGeneration(chatId, isFirstTurn, trimmed)
                }
            }
        }
    }

    fun regenerateLast() {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        val active = activeModel.value
        if (active == null) {
            _loadModelWindow.value = true
            return
        }
        val lastAssistant = chatRepo.getMessages(chatId).lastOrNull { it.role == ROLE_ASSISTANT }
            ?: return
        chatRepo.deleteMessage(lastAssistant.id)
        runGeneration(chatId, isFirstTurn = false, userText = "")
    }

    fun deleteMessage(messageId: String) {
        val chatId = _currentChatId.value ?: return
        chatRepo.deleteMessage(messageId)
        _messages.value = chatRepo.getMessages(chatId)
    }

    fun forkFromMessage(messageId: String) {
        if (_isGenerating.value) return
        val sourceChatId = _currentChatId.value ?: return
        val newChat = chatRepo.forkChat(sourceChatId, messageId) ?: return
        selectChat(newChat.id)
    }

    fun editMessage(messageId: String, newContent: String) {
        if (_isGenerating.value) return
        val chatId = _currentChatId.value ?: return
        val trimmed = newContent.trim()
        if (trimmed.isEmpty()) return

        val all = chatRepo.getMessages(chatId)
        val target = all.firstOrNull { it.id == messageId } ?: return
        if (target.content == trimmed) return
        if (target.archivedByCompactId != null) return

        chatRepo.updateMessage(target.copy(content = trimmed))

        if (target.role == ROLE_USER) {
            all.filter { it.timestamp > target.timestamp }
                .forEach { chatRepo.deleteMessage(it.id) }
            _messages.value = chatRepo.getMessages(chatId)
            if (activeModel.value != null) {
                runGeneration(chatId, isFirstTurn = false, userText = trimmed)
            } else {
                _loadModelWindow.value = true
            }
        } else {
            _messages.value = chatRepo.getMessages(chatId)
        }
    }

    fun stopGeneration() {
        if (!_isGenerating.value) return
        modelSession.stopGeneration()
        generationJob?.cancel()
    }

    /**
     * Summarise the active chat via the inference service, then replace the
     * persisted chat history with a single assistant message containing the
     * summary. The KV cache is wiped by the service so the next user turn
     * prefills against the fresh summary-only prefix.
     */
    fun compactConversation() {
        if (_isGenerating.value) return
        if (_compactionState.value.active) return
        val chatId = _currentChatId.value ?: return
        val activeModelName = activeModel.value?.name ?: ""
        val existing = chatRepo.getMessages(chatId)
        if (existing.isEmpty()) return

        val arr = JSONArray()
        existing.forEach { msg ->
            if (msg.role != ROLE_USER && msg.role != ROLE_ASSISTANT) return@forEach
            arr.put(JSONObject().apply {
                put("role", msg.role)
                put("content", msg.content)
            })
        }
        if (arr.length() == 0) return
        val messagesJson = arr.toString()
        val maxSummaryTokens = 768

        val start = System.currentTimeMillis()
        val summary = StringBuilder()
        var tokensIn = 0
        var promptProgress = 0f

        _compactionState.value = CompactionState(active = true, elapsedMs = 0, tokensIn = 0, fraction = 0f)

        compactionJob = viewModelScope.launch {
            try {
                InferenceClient.compactConversation(messagesJson, maxSummaryTokens)
                    .transformWhile { event ->
                        emit(event)
                        event !is InferenceEvent.Done && event !is InferenceEvent.Error
                    }
                    .collect { event ->
                        when (event) {
                            is InferenceEvent.Token -> {
                                summary.append(event.text)
                                val genElapsed = System.currentTimeMillis() - start
                                val genFrac = ((genElapsed - 1500).coerceAtLeast(0)
                                    .toFloat() / GEN_PHASE_TARGET_MS)
                                    .coerceIn(0f, 0.95f)
                                _compactionState.value = _compactionState.value.copy(
                                    elapsedMs = genElapsed,
                                    fraction = (0.5f + genFrac * 0.5f).coerceIn(0f, 0.97f),
                                )
                            }
                            is InferenceEvent.Progress -> {
                                promptProgress = event.progress.coerceIn(0f, 1f)
                                _compactionState.value = _compactionState.value.copy(
                                    elapsedMs = System.currentTimeMillis() - start,
                                    fraction = (promptProgress * 0.5f).coerceIn(0f, 0.5f),
                                )
                            }
                            is InferenceEvent.Metrics -> {
                                val j = runCatching { JSONObject(event.metricsJson) }.getOrNull()
                                if (j != null) {
                                    tokensIn = j.optInt("tokensEvaluated", tokensIn)
                                    _compactionState.value = _compactionState.value.copy(
                                        elapsedMs = System.currentTimeMillis() - start,
                                        tokensIn = tokensIn,
                                    )
                                }
                            }
                            is InferenceEvent.Done -> {
                                _compactionState.value = _compactionState.value.copy(
                                    elapsedMs = System.currentTimeMillis() - start,
                                    fraction = 1f,
                                )
                            }
                            is InferenceEvent.Error -> {
                                Log.w("HomeViewModel", "compact failed: ${event.message}")
                            }
                            else -> Unit
                        }
                    }

                val summaryText = summary.toString().trim()
                if (summaryText.isNotEmpty()) {
                    val compactId = UUID.randomUUID().toString()
                    chatRepo.getMessages(chatId)
                        .filter { it.archivedByCompactId == null }
                        .forEach { msg ->
                            chatRepo.updateMessage(msg.copy(archivedByCompactId = compactId))
                        }
                    chatRepo.addMessage(
                        ChatMessage(
                            id = compactId,
                            chatId = chatId,
                            role = ROLE_ASSISTANT,
                            content = summaryText,
                            timestamp = System.currentTimeMillis(),
                            kind = MessageKind.CompactSummary,
                            modelName = activeModelName,
                        )
                    )
                    _messages.value = chatRepo.getMessages(chatId)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("HomeViewModel", "compact threw: ${e.message}")
            } finally {
                if (_compactionState.value.fraction >= 0.97f) {
                    _compactionState.value = _compactionState.value.copy(fraction = 1f)
                    try { delay(450) } catch (_: CancellationException) {}
                }
                _compactionState.value = CompactionState(active = false)
                compactionJob = null
            }
        }
    }

    companion object {
        private const val GEN_PHASE_TARGET_MS = 12_000L
    }

    private fun runGeneration(chatId: String, isFirstTurn: Boolean, userText: String) {
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = StreamingFragment(chatId, "", "")
        _isGenerating.value = true
        _loadModelWindow.value = false

        val thinkingOn = _thinkingEnabled.value && modelSession.supportsThinking.value
        modelSession.setThinkingEnabled(thinkingOn)
        toolCallCoordinator.configureInference(
            webOn = false,
            userSystemPrompt = modelSession.userSystemPrompt.value,
        )

        generationJob = viewModelScope.launch {
            var outcome: GenerationOutcome? = null
            var fallbackError: String? = null
            var wasStopped = false
            try {
                outcome = inferenceCoordinator.run(
                    chatId = chatId,
                    onStreamingUpdate = { content, thinking ->
                        _streamingFragment.value = StreamingFragment(chatId, content, thinking)
                    },
                    onToolExecuted = { msgs -> _messages.value = msgs },
                    onMetrics = { text, mem ->
                        text?.let { _lastTextMetrics.value = it }
                        mem?.let { _lastMemoryMetrics.value = it }
                    },
                )
            } catch (e: CancellationException) {
                wasStopped = true
            } catch (e: Exception) {
                fallbackError = e.message
            } finally {
                val partial = _streamingFragment.value
                val resolvedContent = outcome?.content?.takeIf { it.isNotBlank() }
                    ?: partial?.content.orEmpty()
                val resolvedThinking = outcome?.thinking?.takeIf { it.isNotBlank() }
                    ?: partial?.thinkingContent.orEmpty()
                finalizeAssistantMessage(
                    chatId = chatId,
                    content = resolvedContent,
                    thinking = resolvedThinking,
                    error = if (wasStopped) null else (outcome?.error ?: fallbackError),
                    isFirstTurn = isFirstTurn,
                    userText = userText,
                    textMetrics = outcome?.textMetrics,
                    memoryMetrics = outcome?.memoryMetrics,
                    wasStopped = wasStopped,
                )
            }
        }
    }

    private fun ensureChat(active: ModelInfo): String {
        val existing = _currentChatId.value
        if (existing != null) return existing
        val chat = chatRepo.createChat(active.id, active.name)
        _currentChatId.value = chat.id
        return chat.id
    }

    private fun finalizeAssistantMessage(
        chatId: String,
        content: String,
        thinking: String,
        error: String?,
        isFirstTurn: Boolean,
        userText: String,
        textMetrics: TextMetrics?,
        memoryMetrics: MemoryMetrics?,
        wasStopped: Boolean = false,
    ) {
        if (wasStopped && content.isBlank() && thinking.isBlank()) {
            _streamingFragment.value = null
            _isGenerating.value = false
            return
        }
        val finalContent = when {
            error != null && content.isBlank() -> "Error: $error"
            error != null -> "$content\n\n_Error: ${error}_"
            wasStopped -> "$content\n\n_Stopped_"
            else -> content
        }
        val finalMessage = ChatMessage(
            id = UUID.randomUUID().toString(),
            chatId = chatId,
            role = ROLE_ASSISTANT,
            content = finalContent,
            thinkingContent = thinking,
            timestamp = System.currentTimeMillis(),
            kind = MessageKind.Text,
            modelName = activeModel.value?.name.orEmpty(),
            textMetrics = textMetrics,
            memoryMetrics = memoryMetrics,
        )
        chatRepo.addMessage(finalMessage)
        if (isFirstTurn) chatRepo.autoTitle(chatId, userText)
        _messages.value = chatRepo.getMessages(chatId)
        _streamingFragment.value = null
        _isGenerating.value = false
    }

    private fun String.wordCount(): Int {
        if (isBlank()) return 0
        return trim().split(WHITESPACE).count { it.isNotEmpty() }
    }
}

/**
 * Snapshot of an in-flight conversation compaction. The UI binds to this and
 * renders a progress banner while [active] is true.
 */
data class CompactionState(
    val active: Boolean = false,
    val elapsedMs: Long = 0L,
    val tokensIn: Int = 0,
    val fraction: Float = 0f,
)

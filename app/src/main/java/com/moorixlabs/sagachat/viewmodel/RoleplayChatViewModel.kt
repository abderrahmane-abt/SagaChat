package com.moorixlabs.sagachat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moorixlabs.sagachat.data.AppPreferences
import com.moorixlabs.sagachat.model.Character
import com.moorixlabs.sagachat.model.ChatMessage
import com.moorixlabs.sagachat.model.MemoryState
import com.moorixlabs.sagachat.model.MessageKind
import com.moorixlabs.sagachat.model.enums.ProviderType
import com.moorixlabs.sagachat.repo.CharacterRepository
import com.moorixlabs.sagachat.repo.ChatRepository
import com.moorixlabs.sagachat.repo.MemoryManager
import com.moorixlabs.sagachat.repo.ModelRepository
import com.moorixlabs.sagachat.util.CharacterExporter
import com.moorixlabs.sagachat.util.SystemPromptBuilder
import com.moorixlabs.sagachat.viewmodel.home_vm.ModelSessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed class RpSessionState {
    data object Loading : RpSessionState()
    data object Ready : RpSessionState()
    data object NoModelInstalled : RpSessionState()
    data class Error(val message: String) : RpSessionState()
}

@HiltViewModel
class RoleplayChatViewModel @Inject constructor(
    private val characterRepo: CharacterRepository,
    private val chatRepo: ChatRepository,
    private val modelRepo: ModelRepository,
    private val memoryManager: MemoryManager,
    private val modelSession: ModelSessionManager,
    private val appPrefs: AppPreferences,
) : ViewModel() {

    private val _character = MutableStateFlow<Character?>(null)
    val character: StateFlow<Character?> = _character.asStateFlow()

    private val _memoryState = MutableStateFlow(MemoryState(characterId = ""))
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    private val _showMemoryPanel = MutableStateFlow(false)
    val showMemoryPanel: StateFlow<Boolean> = _showMemoryPanel.asStateFlow()

    private val _sessionState = MutableStateFlow<RpSessionState>(RpSessionState.Loading)
    val sessionState: StateFlow<RpSessionState> = _sessionState.asStateFlow()

    fun init(characterId: String) {
        // Gate: check for installed GGUF models before anything else
        val hasModel = modelRepo.models.value.any { it.providerType == ProviderType.GGUF }
        if (!hasModel) {
            _sessionState.value = RpSessionState.NoModelInstalled
            return
        }

        val c = characterRepo.getById(characterId)
        if (c == null) {
            _sessionState.value = RpSessionState.Error("Character not found.")
            return
        }
        _character.value = c
        _memoryState.value = memoryManager.get(characterId)
        applyCharacterSystemPrompt(c)
        _sessionState.value = RpSessionState.Ready
    }

    fun toggleMemoryPanel() {
        _showMemoryPanel.value = !_showMemoryPanel.value
    }

    fun dismissMemoryPanel() {
        _showMemoryPanel.value = false
    }

    fun resetMemory() {
        val c = _character.value ?: return
        memoryManager.resetMemory(c.id)
        _memoryState.value = MemoryState(characterId = c.id)
    }

    /**
     * Called by the screen after every assistant message is finalized.
     * Checks if a memory update is due, and if so runs it and re-applies
     * the character system prompt with the fresh memory snapshot.
     */
    fun onTurnCompleted(allMessages: List<ChatMessage>) {
        val c = _character.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val updated = memoryManager.maybeUpdateMemory(
                characterId = c.id,
                allMessages = allMessages,
                charName = c.chatName,
                userName = appPrefs.userDisplayName,
            )
            _memoryState.value = updated
            applyCharacterSystemPrompt(c, updated)
        }
    }

    /**
     * Ensures the character's initial greeting is present as the first
     * assistant message in the chat. Called once when the chat session opens.
     */
    fun ensureInitialMessage(chatId: String) {
        val c = _character.value ?: return
        if (c.initialMessage.isBlank()) return
        val existing = chatRepo.getMessages(chatId)
        if (existing.any { it.role == "assistant" }) return

        val greeting = ChatMessage(
            id        = UUID.randomUUID().toString(),
            chatId    = chatId,
            role      = "assistant",
            content   = c.initialMessage,
            timestamp = System.currentTimeMillis(),
            kind      = MessageKind.Text,
            modelName = "",
        )
        chatRepo.addMessage(greeting)
    }

    /**
     * Resolves or creates the linked Chat for this character,
     * then returns its id so HomeViewModel can select it.
     */
    fun resolveOrCreateChatId(): String {
        val c = _character.value ?: error("Character not loaded")
        if (c.linkedChatId.isNotBlank()) {
            val existing = chatRepo.getChatById(c.linkedChatId)
            if (existing != null) return existing.id
        }
        val activeModel = modelRepo.models.value.firstOrNull { it.isActive && it.providerType == ProviderType.GGUF }
            ?: modelRepo.models.value.firstOrNull { it.providerType == ProviderType.GGUF }
            ?: error("No GGUF model installed")
        val chat = chatRepo.createChat(activeModel.id, activeModel.name)
        characterRepo.linkChat(c.id, chat.id)
        _character.value = c.copy(linkedChatId = chat.id)
        return chat.id
    }

    fun exportSessionJson(messages: List<ChatMessage>): String? {
        val c = _character.value ?: return null
        return CharacterExporter.exportSession(c, messages)
    }

    fun exportSessionFileName(): String {
        val c = _character.value ?: return "chat_session.json"
        return CharacterExporter.fileName(c).replace(".json", "_chat.json")
    }

    private fun applyCharacterSystemPrompt(
        character: Character,
        memory: MemoryState = memoryManager.get(character.id),
    ) {
        val prompt = SystemPromptBuilder.build(
            character = character,
            memory    = memory,
            userName  = appPrefs.userDisplayName,
        )
        // Write to ModelSessionManager so HomeViewModel.runGeneration()
        // reads the character prompt instead of the empty ModelConfig value.
        modelSession.setUserSystemPrompt(prompt)
    }
}

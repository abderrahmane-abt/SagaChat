# Phase 05 — ViewModels

Two new viewmodels. `CharacterViewModel` owns the character list and
creation flow. `RoleplayChatViewModel` owns a single roleplay session,
wiring the character card → system prompt → inference → memory loop.

Both live in `viewmodel/`. Both use `@HiltViewModel`.

---

## 1. `viewmodel/CharacterViewModel.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/viewmodel/CharacterViewModel.kt`

```kotlin
package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import com.dark.tool_neuron.model.Character
import com.dark.tool_neuron.repo.CharacterRepository
import com.dark.tool_neuron.repo.MemoryManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class CharacterViewModel @Inject constructor(
    private val characterRepo: CharacterRepository,
    private val memoryManager: MemoryManager,
) : ViewModel() {

    val characters: StateFlow<List<Character>> = characterRepo.characters

    // Draft state used by CharacterCreateScreen
    private val _draft = MutableStateFlow(emptyDraft())
    val draft: StateFlow<Character> = _draft.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    fun loadDraft(characterId: String) {
        val existing = characterRepo.getById(characterId) ?: return
        _draft.value = existing
    }

    fun resetDraft() {
        _draft.value = emptyDraft()
    }

    fun updateDraftName(value: String)           { _draft.value = _draft.value.copy(name = value) }
    fun updateDraftChatName(value: String)        { _draft.value = _draft.value.copy(chatName = value) }
    fun updateDraftBio(value: String)             { _draft.value = _draft.value.copy(bio = value) }
    fun updateDraftPersonality(value: String)     { _draft.value = _draft.value.copy(personality = value) }
    fun updateDraftScenario(value: String)        { _draft.value = _draft.value.copy(scenario = value) }
    fun updateDraftInitialMessage(value: String)  { _draft.value = _draft.value.copy(initialMessage = value) }
    fun updateDraftExampleDialogs(value: String)  { _draft.value = _draft.value.copy(exampleDialogs = value) }
    fun updateDraftTags(tags: List<String>)       { _draft.value = _draft.value.copy(tags = tags) }
    fun updateDraftAvatarUri(uri: String)         { _draft.value = _draft.value.copy(avatarUri = uri) }

    fun saveCharacter(onDone: (characterId: String) -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        val d = _draft.value
        val saved = if (d.id.isBlank()) {
            characterRepo.create(d)
        } else {
            characterRepo.update(d)
            d
        }
        _isSaving.value = false
        resetDraft()
        onDone(saved.id)
    }

    fun deleteCharacter(characterId: String) {
        characterRepo.delete(characterId)
        memoryManager.resetMemory(characterId)
    }

    fun getCharacter(characterId: String): Character? =
        characterRepo.getById(characterId)

    private fun emptyDraft() = Character(
        id             = "",
        name           = "",
        chatName       = "",
        bio            = "",
        personality    = "",
        scenario       = "",
        initialMessage = "",
        exampleDialogs = "",
    )
}
```

---

## 2. `viewmodel/RoleplayChatViewModel.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/viewmodel/RoleplayChatViewModel.kt`

This VM owns a single roleplay session. It reuses `HomeViewModel`'s
inference machinery (`InferenceCoordinator`, `ModelSessionManager`,
`ToolCallCoordinator`) by delegating the actual generation to
`HomeViewModel.sendMessage()` — but before each send it injects the
character system prompt via `ToolCallCoordinator.configureInference`.

```kotlin
package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.Character
import com.dark.tool_neuron.model.ChatMessage
import com.dark.tool_neuron.model.MemoryState
import com.dark.tool_neuron.repo.CharacterRepository
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.MemoryManager
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.util.SystemPromptBuilder
import com.dark.tool_neuron.viewmodel.home_vm.ModelSessionManager
import com.dark.tool_neuron.viewmodel.home_vm.ToolCallCoordinator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class RoleplayChatViewModel @Inject constructor(
    private val characterRepo: CharacterRepository,
    private val chatRepo: ChatRepository,
    private val modelRepo: ModelRepository,
    private val memoryManager: MemoryManager,
    private val modelSession: ModelSessionManager,
    private val toolCallCoordinator: ToolCallCoordinator,
    private val appPrefs: AppPreferences,
) : ViewModel() {

    private val _character = MutableStateFlow<Character?>(null)
    val character: StateFlow<Character?> = _character.asStateFlow()

    private val _memoryState = MutableStateFlow(MemoryState(characterId = ""))
    val memoryState: StateFlow<MemoryState> = _memoryState.asStateFlow()

    private val _showMemoryPanel = MutableStateFlow(false)
    val showMemoryPanel: StateFlow<Boolean> = _showMemoryPanel.asStateFlow()

    private val _initError = MutableStateFlow<String?>(null)
    val initError: StateFlow<String?> = _initError.asStateFlow()

    fun init(characterId: String) {
        val c = characterRepo.getById(characterId)
        if (c == null) {
            _initError.value = "Character not found."
            return
        }
        _character.value = c
        _memoryState.value = memoryManager.get(characterId)
        applyCharacterSystemPrompt(c)
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

    // Called by HomeViewModel after every assistant message is finalized.
    // Checks whether a memory update is due and reapplies the system prompt.
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
            // Re-inject system prompt with fresh memory snapshot
            applyCharacterSystemPrompt(c, updated)
        }
    }

    // Ensures the character's initial greeting is present as the first
    // assistant message. Called once when a chat session opens.
    fun ensureInitialMessage(
        chatId: String,
        messages: List<ChatMessage>,
    ) {
        val c = _character.value ?: return
        if (c.initialMessage.isBlank()) return
        if (messages.any { it.role == "assistant" }) return

        val greeting = ChatMessage(
            id        = UUID.randomUUID().toString(),
            chatId    = chatId,
            role      = "assistant",
            content   = c.initialMessage,
            timestamp = System.currentTimeMillis(),
            modelName = "",
        )
        chatRepo.addMessage(greeting)
    }

    // Resolves or creates the linked Chat for this character, then returns
    // its id so HomeViewModel can select it.
    fun resolveOrCreateChatId(): String {
        val c = _character.value ?: error("Character not loaded")
        if (c.linkedChatId.isNotBlank()) {
            val existing = chatRepo.getChatById(c.linkedChatId)
            if (existing != null) return existing.id
        }
        val activeModel = modelRepo.models.value.firstOrNull { it.isActive }
            ?: modelRepo.models.value.firstOrNull()
            ?: error("No model installed")
        val chat = chatRepo.createChat(activeModel.id, activeModel.name)
        characterRepo.linkChat(c.id, chat.id)
        _character.value = c.copy(linkedChatId = chat.id)
        return chat.id
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
        // Inject into ToolCallCoordinator which applies it to every
        // inference call. webOn = false — no web search in RP mode.
        toolCallCoordinator.configureInference(
            webOn            = false,
            userSystemPrompt = prompt,
        )
    }
}
```

---

## 3. Bridging `RoleplayChatViewModel` to `HomeViewModel`

`RoleplayChatViewModel` sets the system prompt and owns character/memory
state. `HomeViewModel` runs the actual generation loop and owns the message
stream. The screen (`RoleplayChatScreen`, built in Phase 09) receives both
VMs and coordinates them.

The bridge contract is:

| Responsibility | Owner |
|---|---|
| System prompt injection | `RoleplayChatViewModel.applyCharacterSystemPrompt` |
| Sending a message | `HomeViewModel.sendMessage(text)` |
| Message stream | `HomeViewModel.messages` |
| Streaming fragment | `HomeViewModel.streamingFragment` |
| Model load state | `HomeViewModel.modelLoadState` |
| Stopping generation | `HomeViewModel.stopGeneration()` |
| Chat selection | `HomeViewModel.selectChat(chatId)` |
| Memory + memory update trigger | `RoleplayChatViewModel.onTurnCompleted(messages)` |
| Character card data | `RoleplayChatViewModel.character` |
| Memory panel open/close | `RoleplayChatViewModel.showMemoryPanel` |

In `RoleplayChatScreen`, when `HomeViewModel.isGenerating` flips from
`true` → `false`, call `rpVm.onTurnCompleted(homeVm.messages.value)` to
trigger the memory update check.

---

## 4. Verification

```bash
./gradlew :app:compileDebugKotlin
```

Both viewmodels must compile. Remaining unresolved symbols:
- `CharacterListScreen`, `CharacterCreateScreen`, `CharacterDetailScreen`,
  `RoleplayChatScreen` (screen files, built in Phases 07–09).

These cause compile errors only in `TNavigation.kt` — all other files
should be clean.

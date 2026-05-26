package com.moorixlabs.sagachat.viewmodel

import androidx.lifecycle.ViewModel
import com.moorixlabs.sagachat.model.Character
import com.moorixlabs.sagachat.repo.CharacterRepository
import com.moorixlabs.sagachat.repo.MemoryManager
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

# Phase 03 — Character Data Layer

Create the `Character` and `MemoryState` data models, then implement their
HXS-backed repositories following the exact same pattern as
`ChatRepository.kt`.

All new files go in the package `com.dark.tool_neuron`.

---

## 1. `model/Character.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/model/Character.kt`

```kotlin
package com.dark.tool_neuron.model

data class Character(
    val id: String,
    val name: String,
    val chatName: String,
    val bio: String,
    val personality: String,
    val scenario: String,
    val initialMessage: String,
    val exampleDialogs: String,
    val tags: List<String> = emptyList(),
    val avatarUri: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val linkedChatId: String = "",
)
```

---

## 2. `model/MemoryState.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/model/MemoryState.kt`

```kotlin
package com.dark.tool_neuron.model

data class MemoryState(
    val characterId: String,
    val summary: String = "",
    val entityJson: String = "{}",
    val lastSummarizedTurnIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)
```

The `entityJson` field holds a compact JSON object tracking key facts:
```json
{
  "user_name": "Alex",
  "relationship": "boyfriend",
  "key_events": ["Jace spent last money on cigarettes"],
  "char_mood": "defensive"
}
```

---

## 3. `repo/CharacterRepository.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/repo/CharacterRepository.kt`

```kotlin
package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.Character
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CharacterRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()
    private val _characters = MutableStateFlow<List<Character>>(emptyList())
    val characters: StateFlow<List<Character>> = _characters.asStateFlow()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val path = dir.absolutePath
        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)
        val opened = openOrRebuild(path, dek, userKey)
        if (!opened) throw SecurityException("Failed to open char_store vault")
        storage.ensureCollection(COL_CHARS)
        storage.addIndex(COL_CHARS, TAG_ID, HexStorage.WIRE_BYTES)
        refresh()
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    private fun refresh() {
        _characters.value = storage.getAll(COL_CHARS)
            .map { it.toCharacter() }
            .sortedByDescending { it.updatedAt }
    }

    fun create(character: Character): Character {
        val c = character.copy(
            id = UUID.randomUUID().toString(),
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
        )
        storage.put(COL_CHARS, c.toRecord())
        storage.flush(COL_CHARS)
        refresh()
        return c
    }

    fun update(character: Character) {
        val updated = character.copy(updatedAt = System.currentTimeMillis())
        storage.queryString(COL_CHARS, TAG_ID, character.id)
            .forEach { storage.delete(COL_CHARS, it.id) }
        storage.put(COL_CHARS, updated.toRecord())
        storage.flush(COL_CHARS)
        refresh()
    }

    fun delete(characterId: String) {
        storage.queryString(COL_CHARS, TAG_ID, characterId)
            .forEach { storage.delete(COL_CHARS, it.id) }
        storage.flush(COL_CHARS)
        refresh()
    }

    fun getById(characterId: String): Character? =
        storage.queryString(COL_CHARS, TAG_ID, characterId)
            .firstOrNull()?.toCharacter()

    fun linkChat(characterId: String, chatId: String) {
        val c = getById(characterId) ?: return
        update(c.copy(linkedChatId = chatId))
    }

    companion object {
        private const val SECURE_DIR = "char_store"
        private const val USER_KEY_INFO = "tn.chars.user_key.v1"
        private const val COL_CHARS = "characters"

        private const val TAG_ID              = 1
        private const val TAG_NAME            = 2
        private const val TAG_CHAT_NAME       = 3
        private const val TAG_BIO             = 4
        private const val TAG_PERSONALITY     = 5
        private const val TAG_SCENARIO        = 6
        private const val TAG_INITIAL_MESSAGE = 7
        private const val TAG_EXAMPLE_DIALOGS = 8
        private const val TAG_TAGS            = 9
        private const val TAG_AVATAR_URI      = 10
        private const val TAG_CREATED_AT      = 11
        private const val TAG_UPDATED_AT      = 12
        private const val TAG_LINKED_CHAT_ID  = 13
    }

    private fun Character.toRecord(): HxsRecord {
        val c = this
        return HxsRecord.build {
            putString(TAG_ID, c.id)
            putString(TAG_NAME, c.name)
            putString(TAG_CHAT_NAME, c.chatName)
            putString(TAG_BIO, c.bio)
            putString(TAG_PERSONALITY, c.personality)
            putString(TAG_SCENARIO, c.scenario)
            putString(TAG_INITIAL_MESSAGE, c.initialMessage)
            putString(TAG_EXAMPLE_DIALOGS, c.exampleDialogs)
            putString(TAG_TAGS, JSONArray(c.tags).toString())
            putString(TAG_AVATAR_URI, c.avatarUri)
            putTimestamp(TAG_CREATED_AT, c.createdAt)
            putTimestamp(TAG_UPDATED_AT, c.updatedAt)
            putString(TAG_LINKED_CHAT_ID, c.linkedChatId)
        }
    }

    private fun HxsRecord.toCharacter(): Character {
        val tagsJson = getString(TAG_TAGS)
        val tagList = if (tagsJson.isNotEmpty()) {
            val arr = JSONArray(tagsJson)
            (0 until arr.length()).map { arr.getString(it) }
        } else emptyList()
        return Character(
            id             = getString(TAG_ID),
            name           = getString(TAG_NAME),
            chatName       = getString(TAG_CHAT_NAME),
            bio            = getString(TAG_BIO),
            personality    = getString(TAG_PERSONALITY),
            scenario       = getString(TAG_SCENARIO),
            initialMessage = getString(TAG_INITIAL_MESSAGE),
            exampleDialogs = getString(TAG_EXAMPLE_DIALOGS),
            tags           = tagList,
            avatarUri      = getString(TAG_AVATAR_URI),
            createdAt      = getTimestamp(TAG_CREATED_AT),
            updatedAt      = getTimestamp(TAG_UPDATED_AT),
            linkedChatId   = getString(TAG_LINKED_CHAT_ID),
        )
    }
}
```

---

## 4. `repo/MemoryRepository.kt`

Create file: `app/src/main/java/com/dark/tool_neuron/repo/MemoryRepository.kt`

```kotlin
package com.dark.tool_neuron.repo

import android.content.Context
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import com.dark.hxs_encryptor.HxsEncryptor
import com.dark.tool_neuron.data.AppKeyStore
import com.dark.tool_neuron.model.MemoryState
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val keyStore: AppKeyStore,
    private val encryptor: HxsEncryptor,
) {
    private val storage = HexStorage()

    init {
        val dir = File(context.filesDir, SECURE_DIR).apply { mkdirs() }
        val path = dir.absolutePath
        val dek = keyStore.unwrapOrCreateDek()
        val signerHash = keyStore.installSignerHash()
        val userKey = encryptor.deriveKey(ikm = dek, salt = signerHash, info = USER_KEY_INFO)
        val opened = openOrRebuild(path, dek, userKey)
        if (!opened) throw SecurityException("Failed to open memory_store vault")
        storage.ensureCollection(COL_MEMORY)
        storage.addIndex(COL_MEMORY, TAG_CHARACTER_ID, HexStorage.WIRE_BYTES)
    }

    private fun openOrRebuild(base: String, dek: ByteArray, userKey: ByteArray): Boolean {
        if (storage.exists(base)) {
            if (storage.openEncrypted(base, dek, userKey, encryptor)) return true
            File(base).deleteRecursively()
            File(base).mkdirs()
        }
        return storage.createEncrypted(base, dek, userKey, encryptor)
    }

    fun get(characterId: String): MemoryState =
        storage.queryString(COL_MEMORY, TAG_CHARACTER_ID, characterId)
            .firstOrNull()?.toMemoryState()
            ?: MemoryState(characterId = characterId)

    fun save(state: MemoryState) {
        storage.queryString(COL_MEMORY, TAG_CHARACTER_ID, state.characterId)
            .forEach { storage.delete(COL_MEMORY, it.id) }
        storage.put(COL_MEMORY, state.toRecord())
        storage.flush(COL_MEMORY)
    }

    fun delete(characterId: String) {
        storage.queryString(COL_MEMORY, TAG_CHARACTER_ID, characterId)
            .forEach { storage.delete(COL_MEMORY, it.id) }
        storage.flush(COL_MEMORY)
    }

    companion object {
        private const val SECURE_DIR = "memory_store"
        private const val USER_KEY_INFO = "tn.memory.user_key.v1"
        private const val COL_MEMORY = "memory"

        private const val TAG_CHARACTER_ID          = 1
        private const val TAG_SUMMARY               = 2
        private const val TAG_ENTITY_JSON           = 3
        private const val TAG_LAST_SUMMARIZED_INDEX = 4
        private const val TAG_UPDATED_AT            = 5
    }

    private fun MemoryState.toRecord(): HxsRecord {
        val m = this
        return HxsRecord.build {
            putString(TAG_CHARACTER_ID, m.characterId)
            putString(TAG_SUMMARY, m.summary)
            putString(TAG_ENTITY_JSON, m.entityJson)
            putTimestamp(TAG_LAST_SUMMARIZED_INDEX, m.lastSummarizedTurnIndex.toLong())
            putTimestamp(TAG_UPDATED_AT, m.updatedAt)
        }
    }

    private fun HxsRecord.toMemoryState(): MemoryState = MemoryState(
        characterId             = getString(TAG_CHARACTER_ID),
        summary                 = getString(TAG_SUMMARY),
        entityJson              = getString(TAG_ENTITY_JSON).ifEmpty { "{}" },
        lastSummarizedTurnIndex = getTimestamp(TAG_LAST_SUMMARIZED_INDEX).toInt(),
        updatedAt               = getTimestamp(TAG_UPDATED_AT),
    )
}
```

---

## 5. Hilt module — register new repositories

Open the existing Hilt module that provides `ChatRepository` (likely
`data/AppModule.kt` or `di/AppModule.kt`). Add `@Provides` or confirm
`@Singleton @Inject constructor` is sufficient.

Since both new repositories use `@Singleton` and `@Inject constructor`,
Hilt will auto-provide them without needing an explicit `@Provides` block,
as long as the component scan includes the `repo` package. No action needed
unless the existing module has explicit provides for `ChatRepository` — in
that case, follow the same pattern.

---

## 6. Verification

```bash
./gradlew :app:compileDebugKotlin
```

The two new repository files must compile without errors. If `HexStorage` or
`HxsRecord` can't be found, check the `:hxs` module is still in
`settings.gradle.kts` and in `app/build.gradle.kts` as
`implementation(project(":hxs"))`.

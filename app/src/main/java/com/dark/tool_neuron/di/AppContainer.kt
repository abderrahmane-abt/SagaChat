package com.dark.tool_neuron.di

import android.app.Application
import android.content.Context
import android.util.Log
import com.dark.tool_neuron.data.VaultManager
import com.dark.tool_neuron.database.AppDatabase
import com.dark.tool_neuron.repo.ChatRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.ums.UmsKnowledgeRepository
import com.dark.tool_neuron.repo.ums.UmsMemoryRepository
import com.dark.tool_neuron.repo.ums.UmsPersonaRepository
import com.dark.tool_neuron.viewmodel.factory.ChatListViewModelFactory
import com.dark.tool_neuron.viewmodel.factory.ChatViewModelFactory
import com.dark.tool_neuron.viewmodel.factory.LLMModelViewModelFactory
import com.dark.tool_neuron.worker.ChatManager
import com.dark.tool_neuron.worker.GenerationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.StateFlow

object AppContainer {

    // Room database kept for RAG (FTS4) and migration reads only
    private lateinit var database: AppDatabase
    private lateinit var modelRepository: ModelRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var llmModelViewModelFactory: LLMModelViewModelFactory
    private lateinit var chatListViewModelFactory: ChatListViewModelFactory
    private lateinit var chatViewModelFactory: ChatViewModelFactory

    private val appScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val chatManager = ChatManager()
    private var generationManager = GenerationManager()

    private lateinit var appContext: Context

    private const val TAG = "AppContainer"

    fun init(context: Context, application: Application) {
        appContext = context.applicationContext

        // Keep Room database for RAG (FTS4) and one-time migration reads
        database = AppDatabase.getDatabase(context)

        // Initialize UMS storage (plaintext by default; VaultGateScreen can switch to encrypted)
        if (!VaultManager.isReady.value) {
            val ok = VaultManager.initPlaintext(context)
            if (!ok) Log.e(TAG, "VaultManager plaintext init failed")
        }

        modelRepository = ModelRepository(
            modelRepo = VaultManager.modelRepo!!,
            configRepo = VaultManager.configRepo!!
        )

        chatRepository = ChatRepository()

        llmModelViewModelFactory = LLMModelViewModelFactory(application, modelRepository)
        chatListViewModelFactory = ChatListViewModelFactory(chatManager)
        chatViewModelFactory = ChatViewModelFactory(context, chatManager, generationManager)
    }

    fun ensureVaultInitialized() {
        if (!VaultManager.isReady.value && ::appContext.isInitialized) {
            val ok = VaultManager.initPlaintext(appContext)
            if (!ok) Log.e(TAG, "VaultManager re-init failed")
        }
    }

    fun shutdown() {
        VaultManager.close()
    }

    /**
     * Close the Room database for backup/restore operations.
     */
    fun closeDatabase() {
        AppDatabase.closeDatabase()
    }

    /**
     * Close UMS storage for backup/restore operations.
     */
    fun closeUms() {
        VaultManager.close()
    }

    /**
     * Re-initialize the entire container after a restore operation.
     */
    fun reinitialize(context: Context) {
        val ctx = context.applicationContext
        appContext = ctx
        database = AppDatabase.getDatabase(ctx)

        VaultManager.close()
        val ok = VaultManager.initPlaintext(ctx)
        if (!ok) Log.e(TAG, "VaultManager reinit failed")

        modelRepository = ModelRepository(
            modelRepo = VaultManager.modelRepo!!,
            configRepo = VaultManager.configRepo!!
        )

        chatRepository = ChatRepository()
    }

    // Room database — for RAG (FTS4) and migration only
    fun getDatabase(): AppDatabase = database

    fun getModelRepository(): ModelRepository = modelRepository

    fun getChatRepository(): ChatRepository = chatRepository

    fun getLLMModelViewModelFactory(): LLMModelViewModelFactory = llmModelViewModelFactory

    fun getChatListViewModelFactory(): ChatListViewModelFactory = chatListViewModelFactory

    fun getChatViewModelFactory(): ChatViewModelFactory = chatViewModelFactory

    fun isVaultReady(): Boolean = VaultManager.isReady.value

    val vaultReadyState: StateFlow<Boolean> = VaultManager.isReady

    fun getPersonaRepo(): UmsPersonaRepository =
        VaultManager.personaRepo ?: error("VaultManager not initialized")

    fun getMemoryRepo(): UmsMemoryRepository =
        VaultManager.memoryRepo ?: error("VaultManager not initialized")

    fun getKnowledgeRepo(): UmsKnowledgeRepository =
        VaultManager.knowledgeRepo ?: error("VaultManager not initialized")

    fun getGenerationManager(): GenerationManager = generationManager
}
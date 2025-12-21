package com.mp.user_data

import android.content.Context
import android.util.Log
import com.mp.user_data.helpers.ChatHelper
import com.mp.user_data.helpers.LogHelper
import com.mp.user_data.helpers.MemoryHelper
import com.mp.user_data.models.BrainException
import com.mp.user_data.models.BrainStats
import com.mp.user_data.ntds.getBrainFilePath
import com.mp.user_data.ntds.getOrCreateHardwareBackedAesKey
import com.mp.user_data.ntds.loadEncryptedTree
import com.mp.user_data.ntds.neuron_tree.NeuronNode
import com.mp.user_data.ntds.neuron_tree.NeuronTree
import com.mp.user_data.ntds.neuron_tree.NodeData
import com.mp.user_data.ntds.neuron_tree.NodeType
import com.mp.user_data.ntds.saveEncryptedTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.crypto.SecretKey

/**
 * Central coordinator for all brain data operations.
 * Singleton that manages the NeuronTree and provides access to domain-specific helpers.
 *
 * Usage:
 * ```kotlin
 * val brain = BrainManager.getInstance(context)
 * brain.chatHelper.addChat(chatData)
 * brain.save()
 * ```
 */
class BrainManager private constructor(
    private val keyAlias: String = DEFAULT_KEY_ALIAS
) {

    companion object {
        private const val TAG = "BrainManager"
        private const val DEFAULT_KEY_ALIAS = "brain_key"

        @Volatile
        private var INSTANCE: BrainManager? = null

        /**
         * Get the singleton instance of BrainManager.
         * Initializes on first call.
         */
        fun getInstance(context: Context, keyAlias: String = DEFAULT_KEY_ALIAS): BrainManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BrainManager(
                    keyAlias
                ).also {
                    INSTANCE = it
                    it.initialize(context.applicationContext)
                }
            }
        }

        /**
         * Clear the singleton instance (useful for testing).
         */
        fun clearInstance() {
            synchronized(this) {
                INSTANCE?.cleanup()
                INSTANCE = null
            }
        }
    }

    // Core components
    private lateinit var tree: NeuronTree
    private lateinit var encryptionKey: SecretKey

    // Helpers (lazy initialization)
    val chatHelper: ChatHelper by lazy { ChatHelper(tree) }
    val memoryHelper: MemoryHelper by lazy { MemoryHelper(tree) }
    val logHelper: LogHelper by lazy { LogHelper(tree) }

    // Auto-save configuration
    private var autoSaveJob: Job? = null
    private var autoSaveEnabled = false
    private var autoSaveDelayMs = 5000L // 5 seconds default

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * Initialize the BrainManager by loading or creating the brain file.
     */
    private fun initialize(appContext: Context) {
        Log.d(TAG, "Initializing BrainManager")

        try {
            // Get or create encryption key
            encryptionKey = getOrCreateHardwareBackedAesKey(keyAlias)

            // Load existing tree or create default
            val brainFile = getBrainFilePath(appContext)
            tree = loadEncryptedTree(brainFile, encryptionKey) ?: run {
                Log.i(TAG, "No existing brain file, creating default structure")
                val root = NeuronNode(data = NodeData("", NodeType.ROOT))
                val newTree = NeuronTree(root)
                newTree.apply {
                    migrateBrainStructure(root)
                }
                newTree
            }

            // Save after migration
            save(appContext)

            Log.d(TAG, "BrainManager initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize BrainManager", e)
            throw BrainException("Failed to initialize brain", e)
        }
    }

    /**
     * Save the current tree to disk (synchronous).
     * Should be called on a background thread.
     */
    @Throws(BrainException::class)
    fun save(context: Context) {
        try {
            val brainFile = getBrainFilePath(context.applicationContext)
            saveEncryptedTree(tree, brainFile, encryptionKey)
            Log.d(TAG, "Brain saved successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save brain", e)
            throw BrainException("Failed to save brain", e)
        }
    }

    /**
     * Save the current tree to disk asynchronously.
     * @param context Context for file operations
     * @param onComplete Callback invoked when save completes (on main thread)
     * @param onError Callback invoked if save fails (on main thread)
     */
    fun saveAsync(
        context: Context, onComplete: (() -> Unit)? = null, onError: ((Exception) -> Unit)? = null
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                save(context)
                withContext(Dispatchers.Main) {
                    onComplete?.invoke()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    onError?.invoke(e)
                }
            }
        }
    }

    /**
     * Enable auto-save functionality.
     * The brain will be saved automatically after changes with a debounce delay.
     *
     * @param delayMs Debounce delay in milliseconds (default: 5000ms)
     */
    fun enableAutoSave(delayMs: Long = 5000L) {
        autoSaveEnabled = true
        autoSaveDelayMs = delayMs
        Log.d(TAG, "Auto-save enabled with ${delayMs}ms delay")
    }

    /**
     * Disable auto-save functionality.
     */
    fun disableAutoSave() {
        autoSaveEnabled = false
        autoSaveJob?.cancel()
        autoSaveJob = null
        Log.d(TAG, "Auto-save disabled")
    }

    /**
     * Trigger auto-save if enabled.
     * Call this after making changes to the tree.
     */
    fun triggerAutoSave(context: Context) {
        if (!autoSaveEnabled) return

        // Cancel existing job
        autoSaveJob?.cancel()

        // Schedule new save
        autoSaveJob = coroutineScope.launch(Dispatchers.IO) {
            delay(autoSaveDelayMs)
            try {
                save(context)
                Log.d(TAG, "Auto-save completed")
            } catch (e: Exception) {
                Log.e(TAG, "Auto-save failed", e)
            }
        }
    }

    /**
     * Get direct access to the NeuronTree.
     * Use with caution - prefer using helpers for type safety.
     */
    fun getTree(): NeuronTree = tree

    /**
     * Reload the tree from disk.
     * WARNING: This will discard any unsaved changes!
     */
    fun reload(context: Context) {
        Log.w(TAG, "Reloading brain from disk - unsaved changes will be lost")
        initialize(context.applicationContext)
    }

    /**
     * Export the brain to a specified file path.
     * @param context Context for file operations
     * @param targetPath Full path where the brain should be exported
     */
    fun export(context: Context, targetPath: String) {
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val sourceFile = getBrainFilePath(context.applicationContext)
                val targetFile = java.io.File(targetPath)
                sourceFile.copyTo(targetFile, overwrite = true)
                Log.d(TAG, "Brain exported to: $targetPath")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to export brain", e)
            }
        }
    }

    /**
     * Get statistics about the brain.
     */
    fun getStats(): BrainStats {
        return BrainStats(
            totalNodes = tree.nodeMap.size,
            chatCount = chatHelper.getCount(),
            memoryCount = memoryHelper.getCount(),
            logCount = logHelper.getCount()
        )
    }

    /**
     * Clean up resources.
     * Should be called when the app is closing.
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up BrainManager")
        disableAutoSave()
        coroutineScope.cancel()
    }
}
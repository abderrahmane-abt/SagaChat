package com.dark.plugins.manager

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.dark.plugins.db.DatabaseProvider
import com.dark.plugins.db.PluginLocalDBDao
import com.dark.plugins.model.InstalledPlugin
import com.dark.plugins.model.LoadedPlugin
import com.dark.plugins.model.Tools
import com.dark.plugins.worker.PluginOps
import com.dark.plugins.worker.instantiatePlugin
import com.dark.plugins.worker.loadPluginZipFromPath
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.cancellation.CancellationException

object PluginManager {

    private const val TAG = "PluginManager"

    private val supervisorJob = SupervisorJob()
    private val _runningPlugins = MutableStateFlow<List<LoadedPlugin>>(emptyList())
    private val _currentPlugin = MutableStateFlow<LoadedPlugin?>(null)
    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    private val pluginViewModelStores = mutableMapOf<String, ViewModelStore>()
    private val daoRef = AtomicReference<PluginLocalDBDao?>()

    val pluginScope = CoroutineScope(Dispatchers.IO + supervisorJob)
    val runningPlugins: StateFlow<List<LoadedPlugin>> = _runningPlugins.asStateFlow()
    val currentPlugin: StateFlow<LoadedPlugin?> = _currentPlugin.asStateFlow()
    val installedPlugins: StateFlow<List<InstalledPlugin>> = _installedPlugins.asStateFlow()

    val toolsList: StateFlow<List<Pair<String, List<Tools>>>> =
        _installedPlugins.map { rows -> rows.map { it.pluginName to it.tools } }
            .stateIn(pluginScope, SharingStarted.Eagerly, emptyList())

    // --- Initialization ---
    fun init(context: Context) {
        Log.d(TAG, "PluginManager.init() started")

        if (daoRef.get() != null) {
            Log.d(TAG, "Database already initialized, skipping setup")
            return
        }

        val db = DatabaseProvider.getDatabase(context.applicationContext)
        daoRef.set(db.getInstalledPluginDao())

        pluginScope.launch {
            try {
                daoRef.get()?.getInstalledPlugins()?.collect { rows ->
                    _installedPlugins.value = rows
                }
            } catch (t: Throwable) {
                Log.e(TAG, "DB collection failed", t)
            }
        }
    }

    // --- Plugin Control ---
    fun runPlugin(context: Context, name: String, data: Any): LoadedPlugin {
        val path = installedPlugins.value.find { it.pluginName == name }?.pluginPath
        if (path.isNullOrEmpty()) {
            Log.w(TAG, "Plugin not found: $name")
            return LoadedPlugin(null, null, null, null, IllegalArgumentException("Not installed"))
        }

        val loaded = loadPluginFromFile(File(path), context)
        val api = loaded.api ?: return loaded.also {
            Log.e(TAG, "Plugin API missing: $name", loaded.throwable)
        }

        val pluginId = loaded.manifest?.name ?: api.getPluginInfo().name.ifBlank { name }

        val job = pluginScope.launch {
            try {
                api.onCreate(data)
                awaitCancellation()
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: Exception) {
                Log.e(TAG, "Plugin execution failed: $pluginId", e)
            } finally {
                runCatching { api.onDestroy() }
            }
        }

        val running = loaded.copy(job = job)
        _runningPlugins.value =
            _runningPlugins.value.filterNot { it.displayName() == pluginId } + running
        _currentPlugin.value = running

        job.invokeOnCompletion {
            val updated = _runningPlugins.value.filterNot { it.displayName() == pluginId }
            _runningPlugins.value = updated
            if (_currentPlugin.value?.displayName() == pluginId) _currentPlugin.value =
                updated.firstOrNull()

            pluginViewModelStores.remove(pluginId)?.clear()
        }

        return running
    }

    fun stopPlugin(pluginName: String) {
        Log.d(TAG, "Stopping plugin: $pluginName")

        val current = _runningPlugins.value.toMutableList()
        val idx = current.indexOfFirst { it.displayName() == pluginName }
        if (idx == -1) {
            Log.w(TAG, "Plugin $pluginName not running.")
            return
        }

        val plugin = current.removeAt(idx)
        plugin.job?.cancel()
        _runningPlugins.value = current

        if (_currentPlugin.value?.displayName() == pluginName) _currentPlugin.value =
            current.firstOrNull()

        pluginViewModelStores.remove(pluginName)?.clear()
    }

    fun cancelAll() {
        pluginScope.coroutineContext.cancelChildren()
        _runningPlugins.value = emptyList()
        _currentPlugin.value = null
        clearPluginViewModels()
    }

    fun getViewModelStoreOwner(pluginName: String): ViewModelStoreOwner =
        object : ViewModelStoreOwner {
            override val viewModelStore: ViewModelStore =
                pluginViewModelStores.getOrPut(pluginName) { ViewModelStore() }
        }

    fun clearPluginViewModels() {
        pluginViewModelStores.values.forEach { it.clear() }
        pluginViewModelStores.clear()
    }

    private fun LoadedPlugin.displayName(): String =
        manifest?.name?.ifBlank { null } ?: api?.getPluginInfo()?.name?.ifBlank { null } ?: ""

    // --- Delegated Install/Uninstall ---
    fun installPluginFromAssets(context: Context, assets: Array<String>) {
        pluginScope.launch {
            PluginOps.installFromAssets(context, assets)
        }
    }

    fun installPluginFromPath(context: Context, vararg paths: String) {
        pluginScope.launch {
            PluginOps.installFromPath(paths = paths, context = context)
        }
    }

    fun uninstallPlugin(pluginName: String) {
        pluginScope.launch {
            PluginOps.uninstallPlugin(pluginName)
        }
    }

    // --- Loader Helper ---
    private fun loadPluginFromFile(path: File, context: Context): LoadedPlugin {
        return try {
            val (manifest, dexBuf) = loadPluginZipFromPath(path)
            val classLoader = InMemoryDexClassLoader(dexBuf, context.classLoader)
            val (pluginInstance, content) = instantiatePlugin(
                classLoader,
                manifest.mainClass,
                context
            )
            LoadedPlugin(null, manifest, pluginInstance, content, null)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to load plugin: ${path.name}", t)
            LoadedPlugin(null, null, null, null, t)
        }
    }
}

package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import com.dark.plugins.db.DatabaseProvider
import com.dark.plugins.db.PluginLocalDBDao
import com.dark.plugins.model.InstalledPlugin
import com.dark.plugins.model.LoadedPlugin
import dalvik.system.InMemoryDexClassLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

object PluginOps {

    private const val TAG = "PluginOps"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val initGuard = AtomicBoolean(false)
    private lateinit var dao: PluginLocalDBDao

    private val _installedPlugins = MutableStateFlow<List<InstalledPlugin>>(emptyList())
    val installedPlugins: StateFlow<List<InstalledPlugin>> get() = _installedPlugins.asStateFlow()

    fun init(context: Context) {
        if (initGuard.compareAndSet(false, true)) {
            dao = DatabaseProvider.getDatabase(context.applicationContext).getInstalledPluginDao()
            scope.launch {
                dao.getInstalledPlugins().collect { plugins ->
                    _installedPlugins.value = plugins
                }
            }
        }
    }

    // ---------------------------------------
    // Plugin Installation
    // ---------------------------------------

    /** Install from assets (zip inside assets folder). */
    fun installFromAssets(context: Context, assetNames: Array<String>) {
        scope.launch {
            assetNames.forEach { asset ->
                try {
                    val zip = copyAssetToCache(context, asset)
                    installFromFile(zip, context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Asset install failed: $asset", t)
                }
            }
        }
    }

    /** Install from direct filesystem path. */
    fun installFromPath(vararg paths: String, context: Context) {
        scope.launch {
            paths.forEach { path ->
                try {
                    installFromFile(File(path), context)
                } catch (t: Throwable) {
                    Log.e(TAG, "Path install failed: $path", t)
                }
            }
        }
    }

    /** Core install logic — load, extract, register in DB. */
    private suspend fun installFromFile(file: File, context: Context) =
        withContext(Dispatchers.IO) {
            if (!file.exists()) throw IOException("Plugin file not found: ${file.absolutePath}")

            val loaded = try {
                val (manifest, dexBuf) = loadPluginZipFromPath(file)
                val classLoader = InMemoryDexClassLoader(dexBuf, context.classLoader)
                val (pluginInstance, _) = instantiatePlugin(
                    classLoader,
                    manifest.mainClass,
                    context
                )
                LoadedPlugin(null, manifest, pluginInstance, null, null)
            } catch (t: Throwable) {
                Log.e(TAG, "Failed loading plugin zip ${file.name}", t)
                return@withContext
            }

            val manifest = loaded.manifest ?: return@withContext
            val pluginDir = File(context.filesDir, "plugins/${file.nameWithoutExtension}")

            if (!pluginDir.exists() && !pluginDir.mkdirs()) {
                Log.w(TAG, "Failed to create dir ${pluginDir.absolutePath}")
            }

            val destFile = File(pluginDir, file.name)
            file.copyTo(destFile, overwrite = true)

            Log.d(TAG, "Plugin copied to ${destFile.absolutePath}")

            upsertPlugin(
                InstalledPlugin(
                    pluginName = manifest.name,
                    manifestCode = manifest.rawCode,
                    pluginPath = destFile.absolutePath,
                    mainClass = manifest.mainClass,
                    pluginVersion = manifest.version,
                    tools = manifest.tools
                )
            )
        }

    // ---------------------------------------
    // Plugin Uninstall
    // ---------------------------------------

    fun uninstallPlugin(pluginName: String) {
        scope.launch {
            try {
                val plugin = dao.getByName(pluginName)
                if (plugin == null) {
                    Log.w(TAG, "Uninstall failed — plugin not found: $pluginName")
                    return@launch
                }

                val file = File(plugin.pluginPath)
                val dir = file.parentFile
                val deleted = try {
                    if (dir?.exists() == true) dir.deleteRecursively() else file.delete()
                } catch (t: Throwable) {
                    Log.e(TAG, "Uninstall failed to delete files for $pluginName", t)
                    false
                }

                if (deleted) dao.deleteByName(pluginName)
                else Log.w(TAG, "Uninstall could not delete all files for $pluginName")

            } catch (t: Throwable) {
                Log.e(TAG, "Uninstall error for $pluginName", t)
            }
        }
    }

    // ---------------------------------------
    // Internal helpers
    // ---------------------------------------

    private suspend fun upsertPlugin(plugin: InstalledPlugin) {
        try {
            dao.insertPlugin(plugin)
            Log.d(TAG, "DB upsert success for ${plugin.pluginName}")
        } catch (t: Throwable) {
            Log.e(TAG, "DB upsert failed for ${plugin.pluginName}", t)
        }
    }

    private fun copyAssetToCache(context: Context, name: String): File {
        val out = File(context.cacheDir, name)
        try {
            context.assets.open(name).use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            }
            return out
        } catch (t: Throwable) {
            out.delete()
            throw IOException("Failed copying asset $name", t)
        }
    }

    suspend fun getPluginByName(name: String): InstalledPlugin? =
        withContext(Dispatchers.IO) { dao.getByName(name) }

    fun clearAll() {
        scope.launch { dao.deleteInstalledPlugins() }
    }
}

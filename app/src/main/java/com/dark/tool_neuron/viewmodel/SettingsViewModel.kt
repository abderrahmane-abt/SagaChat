package com.dark.tool_neuron.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.SecurityManager
import com.dark.tool_neuron.data.ThemeController
import com.dark.tool_neuron.model.ModelInfo
import com.dark.tool_neuron.model.enums.ProviderType
import com.dark.tool_neuron.repo.DocumentRepository
import com.dark.tool_neuron.repo.ModelRepository
import com.dark.tool_neuron.repo.RagManager
import com.dark.tool_neuron.ui.icons.TnIcons
import com.dark.tool_neuron.ui.screens.settings.model.SettingsChoiceOption
import com.dark.tool_neuron.ui.screens.settings.model.SettingsDialog
import com.dark.tool_neuron.ui.screens.settings.model.SettingsItem
import com.dark.tool_neuron.ui.screens.settings.model.SettingsSection
import com.dark.tool_neuron.ui.screens.settings.model.SettingsState
import com.dark.tool_neuron.ui.theme.ColorPalette
import com.dark.tool_neuron.util.formatBytes
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val themeController: ThemeController,
    private val security: SecurityManager,
    private val ragManager: RagManager,
    private val modelRepo: ModelRepository,
    private val documentRepo: DocumentRepository,
) : ViewModel() {

    private val _dialog = MutableStateFlow<SettingsDialog?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _diskUsage = MutableStateFlow("")
    private val _lockEnabled = MutableStateFlow(security.isLockEnabled)
    private val appVersion: String = resolveVersion()

    val state: StateFlow<SettingsState> = combine(
        modelRepo.models,
        ragManager.defaultEmbeddingModelId,
        themeController.mode,
        themeController.palette,
        _diskUsage,
        _lockEnabled,
        _dialog,
        _snackbar,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val models = values[0] as List<ModelInfo>
        val defaultEmbedding = values[1] as String?
        val themeMode = values[2] as ThemeController.Mode
        val palette = values[3] as ColorPalette
        val disk = values[4] as String
        val lockOn = values[5] as Boolean
        val dialog = values[6] as SettingsDialog?
        val snackbar = values[7] as String?

        SettingsState(
            sections = buildSections(models, defaultEmbedding, themeMode, palette, disk, lockOn),
            dialog = dialog,
            snackbarMessage = snackbar,
            appVersion = appVersion,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(appVersion = appVersion),
    )

    init { refreshDiskUsage() }

    fun dismissDialog() { _dialog.value = null }

    fun clearSnackbar() { _snackbar.value = null }

    fun requestChoiceDialog(item: SettingsItem.Choice) {
        _dialog.value = SettingsDialog.Choice(
            itemId = item.id,
            title = item.title,
            options = item.options,
            selectedKey = item.selectedKey,
            allowClear = item.id == "default_embedding_model",
            onSelect = item.onSelect,
        )
    }

    private fun buildSections(
        models: List<ModelInfo>,
        defaultEmbedding: String?,
        themeMode: ThemeController.Mode,
        palette: ColorPalette,
        diskUsage: String,
        lockEnabled: Boolean,
    ): List<SettingsSection> = listOf(
        chatAndRagSection(models, defaultEmbedding),
        appearanceSection(themeMode, palette),
        privacySection(lockEnabled),
        storageSection(diskUsage),
        aboutSection(),
    )

    private fun chatAndRagSection(
        models: List<ModelInfo>,
        defaultEmbedding: String?,
    ): SettingsSection {
        val embeddings = models.filter { it.providerType == ProviderType.EMBEDDING }
        val options = embeddings.map {
            SettingsChoiceOption(key = it.id, label = it.name, description = formatBytes(it.fileSize))
        }
        return SettingsSection(
            id = "chat_rag",
            title = "Chat & RAG",
            description = "Defaults for document indexing and retrieval.",
            icon = TnIcons.MessageCircle,
            items = listOf(
                SettingsItem.Choice(
                    id = "default_embedding_model",
                    title = "Default embedding model",
                    subtitle = "Used when you attach a document to a chat.",
                    icon = TnIcons.Database,
                    selectedKey = defaultEmbedding,
                    options = options,
                    emptyMessage = if (options.isEmpty()) "Install one from the Store" else "Auto-pick first",
                    onSelect = { key ->
                        ragManager.setDefaultEmbeddingModelId(key)
                        _dialog.value = null
                    },
                ),
            ),
        )
    }

    private fun appearanceSection(
        themeMode: ThemeController.Mode,
        palette: ColorPalette,
    ): SettingsSection {
        val modeOptions = listOf(
            SettingsChoiceOption(ThemeController.Mode.SYSTEM.name, "Follow system"),
            SettingsChoiceOption(ThemeController.Mode.LIGHT.name, "Light"),
            SettingsChoiceOption(ThemeController.Mode.DARK.name, "Dark"),
        )
        val paletteOptions = ColorPalette.entries.map {
            SettingsChoiceOption(it.name, it.displayName)
        }
        return SettingsSection(
            id = "appearance",
            title = "Appearance",
            description = "Theme and accent colors.",
            icon = TnIcons.Sparkles,
            items = listOf(
                SettingsItem.Choice(
                    id = "theme_mode",
                    title = "Theme",
                    subtitle = "Pick light, dark, or follow system.",
                    icon = TnIcons.Sparkles,
                    selectedKey = themeMode.name,
                    options = modeOptions,
                    onSelect = { key ->
                        if (key != null) themeController.setMode(ThemeController.Mode.valueOf(key))
                        _dialog.value = null
                    },
                ),
                SettingsItem.Choice(
                    id = "color_palette",
                    title = "Color palette",
                    subtitle = "Dynamic pulls from your wallpaper; presets use hand-tuned colors.",
                    icon = TnIcons.Star,
                    selectedKey = palette.name,
                    options = paletteOptions,
                    onSelect = { key ->
                        if (key != null) themeController.setPalette(ColorPalette.valueOf(key))
                        _dialog.value = null
                    },
                ),
            ),
        )
    }

    private fun privacySection(lockEnabled: Boolean): SettingsSection {
        val statusLabel = if (lockEnabled) "Enabled (PIN)" else "Disabled"
        val items = mutableListOf<SettingsItem>(
            SettingsItem.Info(
                id = "lock_status",
                title = "App lock",
                subtitle = "Requires a PIN on launch.",
                icon = TnIcons.Lock,
                value = statusLabel,
            ),
        )
        if (lockEnabled) {
            items += SettingsItem.Action(
                id = "change_pin",
                title = "Change PIN",
                subtitle = "Enter a new 4-or-more digit PIN.",
                icon = TnIcons.Edit,
                onClick = { openPinDialog(isChange = true) },
            )
            items += SettingsItem.Action(
                id = "disable_lock",
                title = "Disable app lock",
                subtitle = "Enter your current PIN to remove it.",
                icon = TnIcons.ShieldCheck,
                destructive = true,
                onClick = { openDisableLockDialog() },
            )
        } else {
            items += SettingsItem.Action(
                id = "enable_lock",
                title = "Enable app lock",
                subtitle = "Set a PIN required on launch.",
                icon = TnIcons.Shield,
                onClick = { openPinDialog(isChange = false) },
            )
        }
        return SettingsSection(
            id = "privacy",
            title = "Privacy",
            description = "App lock and tamper checks.",
            icon = TnIcons.Shield,
            items = items,
        )
    }

    private fun openPinDialog(isChange: Boolean) {
        _dialog.value = SettingsDialog.PinEntry(
            title = if (isChange) "Change PIN" else "Set app lock",
            message = "Enter a PIN of at least 4 digits.",
            minLength = 4,
            onSubmit = { pin ->
                security.setPassword(pin)
                _lockEnabled.value = true
                _dialog.value = null
                _snackbar.value = if (isChange) "PIN updated" else "App lock enabled"
            },
        )
    }

    private fun openDisableLockDialog() {
        _dialog.value = SettingsDialog.PinEntry(
            title = "Disable app lock",
            message = "Enter your current PIN to confirm.",
            minLength = 4,
            confirmLabel = "Disable",
            onSubmit = { pin ->
                if (security.verifyPassword(pin)) {
                    security.disableLock()
                    _lockEnabled.value = false
                    _dialog.value = null
                    _snackbar.value = "App lock disabled"
                } else {
                    _dialog.value = null
                    _snackbar.value = "Incorrect PIN"
                }
            },
        )
    }

    private fun storageSection(diskUsage: String): SettingsSection = SettingsSection(
        id = "storage",
        title = "Storage",
        description = "Indexed documents and cache live in app-private storage.",
        icon = TnIcons.Database,
        items = listOf(
            SettingsItem.Info(
                id = "disk_usage",
                title = "Models on device",
                subtitle = "Total size of downloaded GGUF/ONNX weights.",
                icon = TnIcons.Package,
                value = diskUsage.ifBlank { "Calculating…" },
            ),
            SettingsItem.Action(
                id = "clear_rag_index",
                title = "Clear document index",
                subtitle = "Removes all chat-attached documents and their embeddings.",
                icon = TnIcons.Trash,
                destructive = true,
                onClick = {
                    _dialog.value = SettingsDialog.Confirm(
                        title = "Clear document index?",
                        message = "All documents you attached to chats will be removed. Chats stay; only the RAG index is wiped.",
                        confirmLabel = "Clear",
                        destructive = true,
                        onConfirm = {
                            documentRepo.clearAll()
                            viewModelScope.launch { ragManager.release() }
                            _dialog.value = null
                            _snackbar.value = "Document index cleared"
                        },
                    )
                },
            ),
            SettingsItem.Action(
                id = "clear_cache",
                title = "Clear image & network cache",
                subtitle = "Frees temporary files cached by the app.",
                icon = TnIcons.Refresh,
                onClick = {
                    _dialog.value = SettingsDialog.Confirm(
                        title = "Clear cache?",
                        message = "Cached thumbnails and response bodies will be deleted. Your chats and models are untouched.",
                        confirmLabel = "Clear",
                        destructive = false,
                        onConfirm = {
                            viewModelScope.launch {
                                withContext(Dispatchers.IO) {
                                    context.cacheDir.deleteRecursively()
                                    context.cacheDir.mkdirs()
                                }
                                refreshDiskUsage()
                                _dialog.value = null
                                _snackbar.value = "Cache cleared"
                            }
                        },
                    )
                },
            ),
        ),
    )

    private fun aboutSection(): SettingsSection = SettingsSection(
        id = "about",
        title = "About",
        description = "App info and legal.",
        icon = TnIcons.Info,
        items = listOf(
            SettingsItem.Info(
                id = "version",
                title = "Version",
                icon = TnIcons.InfoCircle,
                value = appVersion,
            ),
            SettingsItem.Info(
                id = "license",
                title = "License",
                subtitle = "Open source, permissive.",
                icon = TnIcons.BookOpen,
                value = "MIT",
            ),
        ),
    )

    private fun refreshDiskUsage() {
        viewModelScope.launch {
            val size = withContext(Dispatchers.IO) {
                dirSize(File(context.filesDir, "models"))
            }
            _diskUsage.value = formatBytes(size)
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        val stack = ArrayDeque<File>()
        stack.addLast(dir)
        while (stack.isNotEmpty()) {
            val f = stack.removeLast()
            if (f.isDirectory) {
                f.listFiles()?.forEach { stack.addLast(it) }
            } else {
                total += f.length()
            }
        }
        return total
    }

    private fun resolveVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    }.getOrDefault("1.0")
}

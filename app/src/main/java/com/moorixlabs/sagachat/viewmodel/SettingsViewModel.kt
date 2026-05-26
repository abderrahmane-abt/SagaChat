package com.moorixlabs.sagachat.viewmodel

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moorixlabs.sagachat.data.AppPreferences
import com.moorixlabs.sagachat.data.SecurityManager
import com.moorixlabs.sagachat.data.VerifyResult
import com.moorixlabs.sagachat.service.inference.InferenceClient
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.repo.ModelRepository
import com.moorixlabs.sagachat.ui.icons.TnIcons
import com.moorixlabs.sagachat.ui.screens.crash_report.CrashReportActivity
import com.moorixlabs.sagachat.ui.screens.settings.model.SettingsChoiceOption
import com.moorixlabs.sagachat.ui.screens.settings.model.SettingsDialog
import com.moorixlabs.sagachat.ui.screens.settings.model.SettingsItem
import com.moorixlabs.sagachat.ui.screens.settings.model.SettingsSection
import com.moorixlabs.sagachat.ui.screens.settings.model.SettingsState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val security: SecurityManager,
    private val modelRepo: ModelRepository,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _navEvents = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navEvents: SharedFlow<String> = _navEvents.asSharedFlow()

    private val _dialog = MutableStateFlow<SettingsDialog?>(null)
    private val _snackbar = MutableStateFlow<String?>(null)
    private val _lockEnabled = MutableStateFlow(security.isLockEnabled)
    private val _panicPinSet = MutableStateFlow(security.hasPanicPin)
    private val _threadMode = MutableStateFlow(prefs.threadMode)
    private val _responseLengthStyle = MutableStateFlow(prefs.responseLengthStyle)
    private val _memoryEnabled = MutableStateFlow(prefs.memoryEnabled)
    private val appVersion: String = resolveVersion()

    val state: StateFlow<SettingsState> = combine(
        _lockEnabled,
        _dialog,
        _snackbar,
        _panicPinSet,
        _threadMode,
        _responseLengthStyle,
        _memoryEnabled,
    ) { args ->
        val lockOn = args[0] as Boolean
        val dialog = args[1] as SettingsDialog?
        val snackbar = args[2] as String?
        val panicSet = args[3] as Boolean
        val threadMode = args[4] as Int
        val responseLength = args[5] as String
        val memoryOn = args[6] as Boolean

        SettingsState(
            sections = buildSections(
                lockEnabled = lockOn,
                panicPinSet = panicSet,
                threadMode = threadMode,
                responseLengthStyle = responseLength,
                memoryEnabled = memoryOn,
            ),
            dialog = dialog,
            snackbarMessage = snackbar,
            appVersion = appVersion,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SettingsState(appVersion = appVersion),
    )

    private val _userDisplayName = MutableStateFlow(prefs.userDisplayName)
    val userDisplayName: StateFlow<String> = _userDisplayName.asStateFlow()

    val responseLengthStyle: StateFlow<String> = _responseLengthStyle.asStateFlow()
    val memoryEnabled: StateFlow<Boolean> = _memoryEnabled.asStateFlow()

    fun setUserDisplayName(v: String) {
        _userDisplayName.value = v
        prefs.userDisplayName = v
    }

    fun setResponseLengthStyle(v: String) {
        _responseLengthStyle.value = v
        prefs.responseLengthStyle = v
    }

    fun setMemoryEnabled(v: Boolean) {
        _memoryEnabled.value = v
        prefs.memoryEnabled = v
    }

    fun dismissDialog() { _dialog.value = null }

    fun clearSnackbar() { _snackbar.value = null }

    fun requestChoiceDialog(item: SettingsItem.Choice) {
        _dialog.value = SettingsDialog.Choice(
            itemId = item.id,
            title = item.title,
            options = item.options,
            selectedKey = item.selectedKey,
            allowClear = false,
            onSelect = item.onSelect,
        )
    }


    private fun buildSections(
        lockEnabled: Boolean,
        panicPinSet: Boolean,
        threadMode: Int,
        responseLengthStyle: String,
        memoryEnabled: Boolean,
    ): List<SettingsSection> = listOf(
        chatRpSection(responseLengthStyle, memoryEnabled),
        modelSection(),
        performanceSection(threadMode),
        privacySection(lockEnabled, panicPinSet),
        diagnosticsSection(),
        aboutSection(),
    )

    private fun chatRpSection(responseLengthStyle: String, memoryEnabled: Boolean): SettingsSection =
        SettingsSection(
            id = SECTION_CHAT_RP,
            title = "Chat & Roleplay",
            description = "Defaults for character interactions and conversation style.",
            icon = TnIcons.MessageCircle,
            items = listOf(
                SettingsItem.Choice(
                    id = ID_RESPONSE_LENGTH,
                    title = "Response length style",
                    subtitle = "How long AI replies aim to be.",
                    icon = TnIcons.MessageCircle,
                    selectedKey = responseLengthStyle,
                    options = listOf(
                        SettingsChoiceOption("short", "Short", "Concise replies, faster generation."),
                        SettingsChoiceOption("medium", "Medium", "Balanced length. Default."),
                        SettingsChoiceOption("long", "Long", "Detailed, immersive responses."),
                    ),
                    onSelect = { key ->
                        val v = key?.takeIf { it in setOf("short", "medium", "long") } ?: "medium"
                        prefs.responseLengthStyle = v
                        _responseLengthStyle.value = v
                        _dialog.value = null
                    },
                ),
                SettingsItem.Toggle(
                    id = ID_MEMORY_ENABLED,
                    title = "Memory",
                    subtitle = "Summarise long conversations to keep context fresh.",
                    icon = TnIcons.Database,
                    checked = memoryEnabled,
                    onToggle = { value ->
                        prefs.memoryEnabled = value
                        _memoryEnabled.value = value
                    },
                ),
            ),
        )

    private fun modelSection(): SettingsSection = SettingsSection(
        id = SECTION_MODEL,
        title = "Model",
        description = "Performance and per-model configuration.",
        icon = TnIcons.Sliders,
        items = listOf(
            SettingsItem.Action(
                id = ID_OPEN_PERFORMANCE,
                title = "Performance",
                subtitle = "Thread mode, CPU placement, decode priority",
                icon = TnIcons.Cpu,
                onClick = { _navEvents.tryEmit(NavScreens.SettingsPerformance.route) },
            ),
            SettingsItem.Action(
                id = ID_OPEN_MODEL_EDITOR,
                title = "Model config editor",
                subtitle = "Tune parameters and prompts on installed models",
                icon = TnIcons.Edit,
                onClick = { _navEvents.tryEmit(NavScreens.ModelManager.route) },
            ),
        ),
    )

    private fun performanceSection(mode: Int): SettingsSection = SettingsSection(
        id = SECTION_PERFORMANCE,
        title = "Performance",
        description = "CPU placement and priority for inference. Persists across model loads.",
        icon = TnIcons.Cpu,
        items = listOf(
            SettingsItem.Choice(
                id = ID_THREAD_MODE,
                title = "Thread mode",
                subtitle = "Balanced is the safe default. Performance pins to perf cluster with higher priority.",
                icon = TnIcons.Cpu,
                selectedKey = mode.toString(),
                options = listOf(
                    SettingsChoiceOption(
                        AppPreferences.THREAD_MODE_POWER_SAVING.toString(),
                        "Power-saving",
                        "1 decode thread pinned to efficiency cluster. Cool & low draw; ~30-40% slower.",
                    ),
                    SettingsChoiceOption(
                        AppPreferences.THREAD_MODE_BALANCED.toString(),
                        "Balanced",
                        "2 decode threads pinned to perf cluster, normal priority. Default.",
                    ),
                    SettingsChoiceOption(
                        AppPreferences.THREAD_MODE_PERFORMANCE.toString(),
                        "Performance",
                        "3 decode threads pinned to perf cluster, high priority, n_batch=1024.",
                    ),
                ),
                onSelect = { key ->
                    val v = key?.toIntOrNull()?.coerceIn(0, 2) ?: AppPreferences.DEFAULT_THREAD_MODE
                    prefs.threadMode = v
                    _threadMode.value = prefs.threadMode
                    InferenceClient.setThreadMode(v)
                    _dialog.value = null
                },
            ),
        ),
    )

    private fun privacySection(lockEnabled: Boolean, panicPinSet: Boolean): SettingsSection {
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
                subtitle = "Enter a new 6-digit PIN.",
                icon = TnIcons.Edit,
                onClick = { openPinDialog(isChange = true) },
            )
            if (panicPinSet) {
                items += SettingsItem.Action(
                    id = "change_panic_pin",
                    title = "Change panic PIN",
                    subtitle = "Replace the existing wipe-on-entry PIN.",
                    icon = TnIcons.AlertTriangle,
                    onClick = { openPanicPinDialog(isChange = true) },
                )
                items += SettingsItem.Action(
                    id = "remove_panic_pin",
                    title = "Remove panic PIN",
                    subtitle = "Stop wiping on a second PIN entry.",
                    icon = TnIcons.Trash,
                    destructive = true,
                    onClick = { openRemovePanicPinDialog() },
                )
            } else {
                items += SettingsItem.Action(
                    id = "set_panic_pin",
                    title = "Set panic PIN",
                    subtitle = "A second PIN that wipes the vault when entered.",
                    icon = TnIcons.AlertTriangle,
                    onClick = { openPanicPinDialog(isChange = false) },
                )
            }
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
            id = SECTION_PRIVACY,
            title = "Privacy",
            description = "App lock and tamper checks.",
            icon = TnIcons.Shield,
            items = items,
        )
    }

    private fun openPanicPinDialog(isChange: Boolean) {
        _dialog.value = SettingsDialog.PinEntry(
            title = if (isChange) "Change panic PIN" else "Set panic PIN",
            message = "Pick a 6-digit PIN that's different from your real one. Entering it instead of the real PIN wipes everything on this device.",
            minLength = 6,
            onSubmit = { pin ->
                viewModelScope.launch {
                    val ok = withContext(Dispatchers.Default) { security.setPanicPin(pin) }
                    _dialog.value = null
                    if (ok) {
                        _panicPinSet.value = true
                        _snackbar.value = if (isChange) "Panic PIN updated" else "Panic PIN set"
                    } else {
                        _snackbar.value = "Couldn't set panic PIN"
                    }
                }
            },
        )
    }

    private fun openRemovePanicPinDialog() {
        _dialog.value = SettingsDialog.Confirm(
            title = "Remove panic PIN?",
            message = "Entering it will no longer wipe the vault. Your real PIN is unaffected.",
            confirmLabel = "Remove",
            destructive = true,
            onConfirm = {
                val ok = security.clearPanicPin()
                _dialog.value = null
                if (ok) {
                    _panicPinSet.value = false
                    _snackbar.value = "Panic PIN removed"
                } else {
                    _snackbar.value = "Couldn't remove panic PIN"
                }
            },
        )
    }

    private fun openPinDialog(isChange: Boolean) {
        _dialog.value = SettingsDialog.PinEntry(
            title = if (isChange) "Change PIN" else "Set app lock",
            message = "Enter a PIN of at least 6 digits.",
            minLength = 6,
            onSubmit = { pin ->
                viewModelScope.launch {
                    withContext(Dispatchers.Default) { security.setPassword(pin) }
                    _lockEnabled.value = true
                    _panicPinSet.value = false
                    _dialog.value = null
                    _snackbar.value = if (isChange) "PIN updated" else "App lock enabled"
                }
            },
        )
    }

    private fun openDisableLockDialog() {
        _dialog.value = SettingsDialog.PinEntry(
            title = "Disable app lock",
            message = "Enter your current PIN to confirm.",
            minLength = 6,
            confirmLabel = "Disable",
            onSubmit = { pin ->
                viewModelScope.launch {
                    val outcome = withContext(Dispatchers.Default) { security.verifyPassword(pin) }
                    when (outcome) {
                        VerifyResult.Success -> {
                            security.disableLock()
                            _lockEnabled.value = false
                            _panicPinSet.value = false
                            _dialog.value = null
                            _snackbar.value = "App lock disabled"
                        }
                        VerifyResult.WrongPin -> {
                            _dialog.value = null
                            _snackbar.value = "Incorrect PIN"
                        }
                        is VerifyResult.LockedOut -> {
                            _dialog.value = null
                            _snackbar.value = "Too many tries — locked until ${outcome.retryAtMs}"
                        }
                        VerifyResult.Wiped -> {
                            _dialog.value = null
                            _lockEnabled.value = false
                            _panicPinSet.value = false
                            _snackbar.value = "Vault wiped"
                        }
                        VerifyResult.NoLock -> {
                            _dialog.value = null
                            _lockEnabled.value = false
                            _panicPinSet.value = false
                        }
                    }
                }
            },
        )
    }

    private fun diagnosticsSection(): SettingsSection = SettingsSection(
        id = SECTION_DIAGNOSTICS,
        title = "Diagnostics",
        description = "Inspect recent errors and crash reports.",
        icon = TnIcons.AlertTriangle,
        items = listOf(
            SettingsItem.Action(
                id = ID_OPEN_CRASH_REPORTS,
                title = "Crash reports",
                subtitle = "Recent errors, native crashes, and the raw JSON bundle.",
                icon = TnIcons.AlertTriangle,
                onClick = {
                    val intent = Intent(context, CrashReportActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    runCatching { context.startActivity(intent) }
                },
            ),
        ),
    )

    private fun aboutSection(): SettingsSection = SettingsSection(
        id = SECTION_ABOUT,
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
            SettingsItem.Action(
                id = "terms",
                title = "Terms of use",
                subtitle = "How this app handles your data.",
                icon = TnIcons.Shield,
                onClick = { _navEvents.tryEmit(NavScreens.TermsConditions.route) },
            ),
            SettingsItem.Action(
                id = "credits",
                title = "Roll the credits",
                subtitle = "See who built this and what it runs on.",
                icon = TnIcons.Star,
                onClick = { _navEvents.tryEmit(NavScreens.Credits.route) },
            ),
        ),
    )

    private fun resolveVersion(): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.versionName ?: "1.0"
    }.getOrDefault("1.0")

    companion object {
        const val SECTION_CHAT_RP = "chat_rp"
        const val SECTION_PERFORMANCE = "performance"
        const val SECTION_MODEL = "model"
        const val SECTION_PRIVACY = "privacy"
        const val SECTION_DIAGNOSTICS = "diagnostics"
        const val SECTION_ABOUT = "about"

        private const val ID_RESPONSE_LENGTH = "rp_response_length"
        private const val ID_MEMORY_ENABLED = "memory_enabled"
        private const val ID_THREAD_MODE = "thread_mode"
        private const val ID_OPEN_PERFORMANCE = "open_performance"
        private const val ID_OPEN_MODEL_EDITOR = "open_model_editor"
        private const val ID_OPEN_CRASH_REPORTS = "open_crash_reports"
    }
}

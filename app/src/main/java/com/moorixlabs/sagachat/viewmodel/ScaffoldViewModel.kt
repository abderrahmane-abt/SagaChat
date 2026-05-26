package com.moorixlabs.sagachat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moorixlabs.download_manager.HxdManager
import com.moorixlabs.download_manager.HxdStatus
import com.moorixlabs.hxs_encryptor.BootIntegrity
import com.moorixlabs.sagachat.TNApplication
import com.moorixlabs.sagachat.data.AccessibilityGuard
import com.moorixlabs.sagachat.data.AppPreferences
import com.moorixlabs.sagachat.data.RootGuard
import com.moorixlabs.sagachat.data.SecurityManager
import com.moorixlabs.sagachat.data.SessionHolder
import com.moorixlabs.sagachat.model.DownloadProgress
import com.moorixlabs.sagachat.model.NavScreens
import com.moorixlabs.sagachat.repo.InstallProgressTracker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class ScaffoldViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val security: SecurityManager,
    private val rootGuard: RootGuard,
    private val accessibilityGuard: AccessibilityGuard,
    session: SessionHolder,
    installProgress: InstallProgressTracker,
) : ViewModel() {

    val shouldLock: StateFlow<Boolean> = combine(
        session.active,
        MutableStateFlow(security.isLockEnabled),
    ) { active, _ ->
        security.isLockEnabled && !active
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = security.isLockEnabled && !session.active.value,
    )

    val downloadProgress: StateFlow<DownloadProgress?> = combine(
        HxdManager.tasks,
        installProgress.extracting,
    ) { tasks, extracting ->
        val active = tasks.filter {
            it.status == HxdStatus.QUEUED ||
                it.status == HxdStatus.CONNECTING ||
                it.status == HxdStatus.DOWNLOADING
        }
        if (active.isEmpty()) {
            return@combine if (extracting.isNotEmpty()) DownloadProgress.Indeterminate else null
        }
        val totals = active.sumOf { it.totalBytes.coerceAtLeast(0L) }
        val anyUnknown = active.any { it.totalBytes <= 0L }
        if (anyUnknown || totals <= 0L) return@combine DownloadProgress.Indeterminate
        val downloaded = active.sumOf { it.downloadedBytes.coerceAtLeast(0L) }
        DownloadProgress.Determinate((downloaded.toFloat() / totals.toFloat()).coerceIn(0f, 1f))
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _rootWarning = MutableStateFlow<RootWarning?>(resolveInitialRootWarning())
    val rootWarning: StateFlow<RootWarning?> = _rootWarning.asStateFlow()

    data class RootWarning(
        val rootEvidence: Set<String>,
        val tamperEvidence: Set<String>,
        val a11yPackages: Set<String>,
    ) {
        val isEmpty: Boolean
            get() = rootEvidence.isEmpty() && tamperEvidence.isEmpty() && a11yPackages.isEmpty()
    }

    private fun resolveInitialRootWarning(): RootWarning? {
        if (prefs.rootWarningShown) return null
        val rootEvidence = when (val r = rootGuard.scan()) {
            is RootGuard.Result.Rooted -> r.evidence
            else -> emptySet()
        }
        val tamperEvidence = mutableSetOf<String>()
        val envBits = TNApplication.softEnvReasons
        if (envBits and BootIntegrity.FAIL_XPOSED != 0) tamperEvidence += "xposed/lspd"
        val a11yPackages = when (val a = accessibilityGuard.scan()) {
            is AccessibilityGuard.Result.SuspiciousAttached -> a.packages
            else -> emptySet()
        }
        val warning = RootWarning(rootEvidence, tamperEvidence.toSet(), a11yPackages)
        return if (warning.isEmpty) null else warning
    }

    fun acknowledgeRootWarning() {
        prefs.rootWarningShown = true
        _rootWarning.value = null
    }

    fun resolveStartDestination(): String {
        val tcAccepted = prefs.tcAccepted
        val secDone = prefs.securitySetupDone
        val modelDone = prefs.modelSetupDone
        if (!tcAccepted) return NavScreens.TermsConditions.route
        if (!secDone) return NavScreens.SetupScreen.route
        if (!modelDone) return NavScreens.ModelSetup.route
        if (security.isLockEnabled) return NavScreens.PasswordScreen.route
        return NavScreens.CharacterList.route
    }

    fun markTermsAccepted() {
        if (!prefs.tcAccepted) prefs.tcAccepted = true
    }

    fun markModelSetupDone() {
        prefs.modelSetupDone = true
    }
}

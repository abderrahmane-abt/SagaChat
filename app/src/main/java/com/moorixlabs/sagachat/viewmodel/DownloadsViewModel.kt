package com.moorixlabs.sagachat.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moorixlabs.download_manager.HxdManager
import com.moorixlabs.download_manager.HxdState
import com.moorixlabs.download_manager.HxdStatus
import com.moorixlabs.sagachat.model.DownloadHistoryEntry
import com.moorixlabs.sagachat.repo.DownloadCoordinator
import com.moorixlabs.sagachat.repo.DownloadHistoryRepository
import com.moorixlabs.sagachat.repo.DownloadLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ActiveDownloadItem(
    val hxdId: Int,
    val displayName: String,
    val type: String,
    val state: HxdState,
)

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val coordinator: DownloadCoordinator,
    private val historyRepo: DownloadHistoryRepository,
) : ViewModel() {

    val activeDownloads: StateFlow<List<ActiveDownloadItem>> = combine(
        HxdManager.tasks,
        coordinator.labelsFlow,
    ) { tasks, labels ->
        tasks.asSequence()
            .filter {
                it.status == HxdStatus.QUEUED ||
                    it.status == HxdStatus.CONNECTING ||
                    it.status == HxdStatus.DOWNLOADING ||
                    it.status == HxdStatus.PAUSED
            }
            .map { state ->
                val label = labels[state.id] ?: DownloadLabel.fromUrl(state.url)
                ActiveDownloadItem(
                    hxdId = state.id,
                    displayName = label.displayName,
                    type = label.type,
                    state = state,
                )
            }
            .sortedBy { it.hxdId }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val history: StateFlow<List<DownloadHistoryEntry>> = historyRepo.history
        .map { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), historyRepo.history.value)

    fun cancel(hxdId: Int) {
        HxdManager.cancel(hxdId)
    }

    fun clearHistory() {
        historyRepo.clearAll()
    }
}

package com.dark.tool_neuron.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dark.tool_neuron.data.AppPreferences
import com.dark.tool_neuron.model.HFRepository
import com.dark.tool_neuron.repo.ExplorerRepo
import com.dark.tool_neuron.repo.GatedFilter
import com.dark.tool_neuron.repo.HfFilters
import com.dark.tool_neuron.repo.HfRepoDetail
import com.dark.tool_neuron.repo.HfSort
import com.dark.tool_neuron.repo.HuggingFaceExplorer
import com.dark.tool_neuron.repo.RepoFile
import com.dark.tool_neuron.repo.RepositoryDataStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

private const val MAX_HISTORY = 20

enum class HfFileFilter { ALL, GGUF, MMPROJ }
enum class HfFileSizeBucket(val label: String, val maxBytes: Long) {
    ANY("Any", Long.MAX_VALUE),
    UNDER_1GB("< 1 GB", 1_073_741_824L),
    UNDER_4GB("< 4 GB", 4L * 1_073_741_824L),
    UNDER_8GB("< 8 GB", 8L * 1_073_741_824L),
}

sealed interface HfRepoDetailState {
    data object Idle : HfRepoDetailState
    data object Loading : HfRepoDetailState
    data class Success(val detail: HfRepoDetail) : HfRepoDetailState
    data class Failed(val reason: String) : HfRepoDetailState
}

@HiltViewModel
class HfExplorerViewModel @Inject constructor(
    private val explorer: HuggingFaceExplorer,
    private val repoDataStore: RepositoryDataStore,
    private val prefs: AppPreferences,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _filters = MutableStateFlow(HfFilters())
    val filters: StateFlow<HfFilters> = _filters.asStateFlow()

    private val _results = MutableStateFlow<List<ExplorerRepo>>(emptyList())
    val results: StateFlow<List<ExplorerRepo>> = _results.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchError = MutableStateFlow<String?>(null)
    val searchError: StateFlow<String?> = _searchError.asStateFlow()

    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<String>> = _history.asStateFlow()

    private val _hideAdded = MutableStateFlow(false)
    val hideAdded: StateFlow<Boolean> = _hideAdded.asStateFlow()

    private val _detailState = MutableStateFlow<HfRepoDetailState>(HfRepoDetailState.Idle)
    val detailState: StateFlow<HfRepoDetailState> = _detailState.asStateFlow()

    private val _fileFilter = MutableStateFlow(HfFileFilter.GGUF)
    val fileFilter: StateFlow<HfFileFilter> = _fileFilter.asStateFlow()

    private val _fileSizeBucket = MutableStateFlow(HfFileSizeBucket.ANY)
    val fileSizeBucket: StateFlow<HfFileSizeBucket> = _fileSizeBucket.asStateFlow()

    val existingRepoPaths: StateFlow<Set<String>> = repoDataStore.repositories
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
        .let { upstream ->
            MutableStateFlow(upstream.value.map { it.repoPath.lowercase() }.toSet()).also { sink ->
                viewModelScope.launch {
                    upstream.collect { list ->
                        sink.value = list.map { it.repoPath.lowercase() }.toSet()
                    }
                }
            }
        }

    private var searchJob: Job? = null
    private var detailJob: Job? = null

    fun setQuery(q: String) {
        _query.value = q
        if (q.isBlank()) {
            _results.value = emptyList()
            _searchError.value = null
        }
    }

    fun search() {
        val q = _query.value.trim()
        if (q.isBlank()) {
            _searchError.value = "Enter a search term"
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            _searchError.value = null
            explorer.searchModels(q, _filters.value).fold(
                onSuccess = { repos ->
                    _results.value = repos
                    if (repos.isEmpty()) _searchError.value = "No repositories matched"
                    pushHistory(q)
                },
                onFailure = { e ->
                    _results.value = emptyList()
                    _searchError.value = e.message ?: "Search failed"
                },
            )
            _isSearching.value = false
        }
    }

    fun searchHistoryEntry(q: String) {
        _query.value = q
        search()
    }

    fun clearHistory() {
        _history.value = emptyList()
        prefs.hfSearchHistory = ""
    }

    fun removeHistoryEntry(q: String) {
        val updated = _history.value.filter { it != q }
        _history.value = updated
        prefs.hfSearchHistory = saveHistory(updated)
    }

    fun setSort(s: HfSort) = update { copy(sort = s) }
    fun setPipelineTag(tag: String?) = update { copy(pipelineTag = tag) }
    fun setAuthor(s: String) = update { copy(author = s) }
    fun setTrainedDataset(s: String) = update { copy(trainedDataset = s) }
    fun setGated(g: GatedFilter) = update { copy(gated = g) }
    fun setInferenceWarm(b: Boolean) = update { copy(inferenceWarm = b) }
    fun setParamsRange(minMillions: Long, maxMillions: Long) =
        update { copy(paramsMinMillions = minMillions, paramsMaxMillions = maxMillions) }

    fun toggleLibrary(value: String) = update { copy(libraries = libraries.toggle(value)) }
    fun toggleApp(value: String) = update { copy(apps = apps.toggle(value)) }
    fun toggleProvider(value: String) = update { copy(providers = providers.toggle(value)) }
    fun toggleLanguage(value: String) = update { copy(languages = languages.toggle(value)) }
    fun toggleLicense(value: String) = update { copy(licenses = licenses.toggle(value)) }
    fun toggleRegion(value: String) = update { copy(regions = regions.toggle(value)) }
    fun toggleOtherTag(value: String) = update { copy(otherTags = otherTags.toggle(value)) }
    fun toggleQuantTag(value: String) = update { copy(quantTags = quantTags.toggle(value)) }

    fun setHideAdded(value: Boolean) { _hideAdded.value = value }

    fun resetFilters() {
        _filters.value = HfFilters()
        _hideAdded.value = false
    }

    fun visibleResults(): List<ExplorerRepo> {
        var list: List<ExplorerRepo> = _results.value
        if (_hideAdded.value) {
            val added = existingRepoPaths.value
            list = list.filter { it.id.lowercase() !in added }
        }
        return list
    }

    fun addRepository(repo: ExplorerRepo) {
        viewModelScope.launch {
            val existing = repoDataStore.repositories.value
            if (existing.any { it.repoPath.equals(repo.id, ignoreCase = true) }) return@launch
            repoDataStore.addRepository(
                HFRepository(
                    id = "hf-${repo.id.replace("/", "-").lowercase()}",
                    name = repo.id.substringAfter("/"),
                    repoPath = repo.id,
                ),
            )
        }
    }

    fun loadRepoDetail(repoPath: String) {
        detailJob?.cancel()
        _detailState.value = HfRepoDetailState.Loading
        detailJob = viewModelScope.launch {
            explorer.fetchRepoDetail(repoPath).fold(
                onSuccess = { _detailState.value = HfRepoDetailState.Success(it) },
                onFailure = { _detailState.value = HfRepoDetailState.Failed(it.message ?: "Failed to load") },
            )
        }
    }

    fun setFileFilter(f: HfFileFilter) { _fileFilter.value = f }
    fun setFileSizeBucket(b: HfFileSizeBucket) { _fileSizeBucket.value = b }

    fun visibleFiles(detail: HfRepoDetail): List<RepoFile> {
        var list: List<RepoFile> = detail.files
        list = when (_fileFilter.value) {
            HfFileFilter.ALL -> list
            HfFileFilter.GGUF -> list.filter { it.path.endsWith(".gguf", ignoreCase = true) }
            HfFileFilter.MMPROJ -> list.filter { it.path.contains("mmproj", ignoreCase = true) }
        }
        val cap = _fileSizeBucket.value.maxBytes
        if (cap != Long.MAX_VALUE) list = list.filter { it.sizeBytes in 1..cap }
        return list.sortedBy { it.sizeBytes }
    }

    private inline fun update(block: HfFilters.() -> HfFilters) {
        _filters.value = _filters.value.block()
    }

    private fun Set<String>.toggle(value: String): Set<String> =
        if (contains(value)) this - value else this + value

    private fun loadHistory(): List<String> {
        val raw = prefs.hfSearchHistory
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
    }

    private fun saveHistory(list: List<String>): String {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    private fun pushHistory(q: String) {
        val trimmed = q.trim()
        if (trimmed.isBlank()) return
        val deduped = (listOf(trimmed) + _history.value.filter { it != trimmed }).take(MAX_HISTORY)
        _history.value = deduped
        prefs.hfSearchHistory = saveHistory(deduped)
    }
}

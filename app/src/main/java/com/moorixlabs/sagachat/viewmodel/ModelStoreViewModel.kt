package com.moorixlabs.sagachat.viewmodel

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.moorixlabs.download_manager.HxdManager
import com.moorixlabs.download_manager.HxdState
import com.moorixlabs.download_manager.HxdStatus
import com.moorixlabs.sagachat.model.HFRepository
import com.moorixlabs.sagachat.model.HuggingFaceModel
import com.moorixlabs.sagachat.model.ModelConfig
import com.moorixlabs.sagachat.model.ModelInfo
import com.moorixlabs.sagachat.model.SizeCategory
import com.moorixlabs.sagachat.model.enums.PathType
import com.moorixlabs.sagachat.model.enums.ProviderType
import com.moorixlabs.sagachat.repo.DownloadCoordinator
import com.moorixlabs.sagachat.repo.ExplorerRepo
import com.moorixlabs.sagachat.repo.HuggingFaceExplorer
import com.moorixlabs.sagachat.repo.InstallProgressTracker
import com.moorixlabs.sagachat.repo.ModelCatalog
import com.moorixlabs.sagachat.repo.ModelRepository
import com.moorixlabs.sagachat.repo.RepositoryDataStore
import com.moorixlabs.sagachat.viewmodel.home_vm.ModelSessionManager
import com.moorixlabs.sagachat.repo.RepositoryValidator
import com.moorixlabs.sagachat.repo.ValidationResult
import com.moorixlabs.sagachat.data.AppPreferences
import com.moorixlabs.sagachat.service.inference.InferenceClient

import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.security.MessageDigest
import javax.inject.Inject

enum class StoreTab { MODELS, INSTALLED, SETTINGS }

@HiltViewModel
class ModelStoreViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelRepo: ModelRepository,
    private val catalog: ModelCatalog,
    private val repoDataStore: RepositoryDataStore,
    private val explorer: HuggingFaceExplorer,
    private val validator: RepositoryValidator,
    private val prefs: AppPreferences,
    private val installProgress: InstallProgressTracker,
    private val modelSession: ModelSessionManager,
    private val downloadCoordinator: DownloadCoordinator,
) : ViewModel() {

    val installedModels: StateFlow<List<ModelInfo>> = modelRepo.models
    val activeDownloadCount: StateFlow<Int> = downloadCoordinator.activeCount
    val repositories: StateFlow<List<HFRepository>> = repoDataStore.repositories

    private val _selectedTab = MutableStateFlow(StoreTab.MODELS)
    val selectedTab: StateFlow<StoreTab> = _selectedTab.asStateFlow()

    private val _models = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val models: StateFlow<List<HuggingFaceModel>> = _models.asStateFlow()

    private val _filteredModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val filteredModels: StateFlow<List<HuggingFaceModel>> = _filteredModels.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _deleteInProgress = MutableStateFlow<String?>(null)
    val deleteInProgress: StateFlow<String?> = _deleteInProgress.asStateFlow()

    private val _deviceInfo = MutableStateFlow<Map<String, String>>(emptyMap())
    val deviceInfo: StateFlow<Map<String, String>> = _deviceInfo.asStateFlow()

    private val _downloadIds = MutableStateFlow<Map<String, Int>>(emptyMap())
    private val _downloadStates = MutableStateFlow<Map<String, HxdState>>(emptyMap())
    val downloadStates: StateFlow<Map<String, HxdState>> = _downloadStates.asStateFlow()


    private val _extractingIds = MutableStateFlow<Set<String>>(emptySet())
    val extractingIds: StateFlow<Set<String>> = _extractingIds.asStateFlow()

    private val _extractingFile = MutableStateFlow<Map<String, String>>(emptyMap())
    val extractingFile: StateFlow<Map<String, String>> = _extractingFile.asStateFlow()

    val isModelLoaded = InferenceClient.isModelLoaded
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    private val _searchQuery = MutableStateFlow("")

    // Validation
    private val _validationResults = MutableStateFlow<Map<String, ValidationResult>>(emptyMap())
    val validationResults: StateFlow<Map<String, ValidationResult>> = _validationResults.asStateFlow()

    // Explorer
    private val _explorerQuery = MutableStateFlow("")
    val explorerQuery: StateFlow<String> = _explorerQuery.asStateFlow()

    private val _explorerResults = MutableStateFlow<List<ExplorerRepo>>(emptyList())
    val explorerResults: StateFlow<List<ExplorerRepo>> = _explorerResults.asStateFlow()

    private val _isExplorerLoading = MutableStateFlow(false)
    val isExplorerLoading: StateFlow<Boolean> = _isExplorerLoading.asStateFlow()

    private val _explorerError = MutableStateFlow<String?>(null)
    val explorerError: StateFlow<String?> = _explorerError.asStateFlow()

    private var explorerSearchJob: Job? = null

    init {
        loadDeviceInfo()
        loadModels()
    }

    fun selectTab(tab: StoreTab) { _selectedTab.value = tab }

    fun loadModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true
            _error.value = null
            try {
                val repos = repoDataStore.repositories.value
                _models.value = catalog.getModels(repos)
                applyAllFilters()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to load models"
            }
            _isLoading.value = false
        }
    }

    fun refreshModels() {
        viewModelScope.launch(Dispatchers.IO) {
            _isRefreshing.value = true
            _error.value = null
            try {
                catalog.clearCache()
                val repos = repoDataStore.repositories.value
                _models.value = catalog.getModels(repos, forceRefresh = true)
                applyAllFilters()
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to refresh"
            }
            _isRefreshing.value = false
        }
    }

    private fun loadDeviceInfo() {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Build.SOC_MODEL else "Unknown"
        _deviceInfo.value = mapOf(
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "soc" to soc,
            "arch" to (Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"),
            "sdk" to "${Build.VERSION.SDK_INT}",
        )
    }


    private fun applyAllFilters() {
        var filtered = _models.value.filter { it.modelType == "gguf" }

        if (_searchQuery.value.isNotBlank()) {
            val q = _searchQuery.value
            filtered = filtered.filter {
                it.name.contains(q, ignoreCase = true) ||
                it.tags.any { t -> t.contains(q, ignoreCase = true) }
            }
        }

        filtered = filtered.sortedBy { it.name.lowercase() }

        _filteredModels.value = filtered
    }

    fun filterModels(query: String) { _searchQuery.value = query; applyAllFilters() }

    fun downloadByQuickStartId(modelId: String) {
        viewModelScope.launch {
            val pool = _filteredModels.value.takeIf { it.isNotEmpty() }
                ?: _filteredModels.first { it.isNotEmpty() }
            enqueueChatModel(pool, modelId)
        }
    }

    fun downloadPack(packId: String) {
        val ids = PACK_CONTENTS[packId] ?: return
        viewModelScope.launch {
            val pool = _models.value.takeIf { it.isNotEmpty() }
                ?: _models.first { it.isNotEmpty() }
            ids.forEach { entry ->
                when (entry.kind) {
                    PackEntryKind.Chat -> enqueueChatModel(pool, entry.id)
                    PackEntryKind.Voice -> enqueueVoiceModel(pool, entry.id)
                }
            }
        }
    }

    private fun enqueueChatModel(pool: List<HuggingFaceModel>, modelId: String) {
        val candidates = pool.filter { it.repoId == modelId || it.id.startsWith(modelId) }
        val preferred = QUICK_START_QUANT_PRIORITY.firstNotNullOfOrNull { q ->
            candidates.firstOrNull { it.quantization.equals(q, ignoreCase = true) }
        } ?: candidates.filter { it.sizeBytes > 0 }.minByOrNull { it.sizeBytes }
            ?: candidates.firstOrNull()
        if (preferred != null) downloadModel(preferred)
    }

    private fun enqueueVoiceModel(pool: List<HuggingFaceModel>, modelId: String) {
        val match = pool.firstOrNull { it.id == modelId } ?: return
        downloadModel(match)
    }

    companion object {
        val QUICK_START_QUANT_PRIORITY = listOf("Q4_K_M", "Q4_K_S", "Q4_0", "Q5_K_M", "Q5_K_S", "Q8_0")

        const val PACK_CHAT_ONLY = "pack_chat_only"
        const val PACK_CHAT_VOICE = "pack_chat_voice"
        const val PACK_LARGE_CHAT_VOICE = "pack_large_chat_voice"

        private val PACK_CONTENTS: Map<String, List<PackEntry>> = mapOf(
            PACK_CHAT_ONLY to listOf(
                PackEntry("lfm25-350m", PackEntryKind.Chat),
            ),
            PACK_CHAT_VOICE to listOf(
                PackEntry("lfm25-350m", PackEntryKind.Chat),
                PackEntry("sherpa-onnx-whisper-tiny-en", PackEntryKind.Voice),
                PackEntry("vits-piper-en_US-amy-low", PackEntryKind.Voice),
            ),
            PACK_LARGE_CHAT_VOICE to listOf(
                PackEntry("qwen3-0.6b", PackEntryKind.Chat),
                PackEntry("sherpa-onnx-whisper-tiny-en", PackEntryKind.Voice),
                PackEntry("vits-piper-en_US-amy-low", PackEntryKind.Voice),
            ),
        )
    }

    private enum class PackEntryKind { Chat, Voice }
    private data class PackEntry(val id: String, val kind: PackEntryKind)

    fun downloadModel(model: HuggingFaceModel) {
        if (_downloadIds.value.containsKey(model.id)) return

        val destFile = when {
            model.modelType == "image_gen" ->
                modelRepo.imageModelArchive(model.id, model.fileName)
            model.modelType == "image_upscaler" ->
                modelRepo.imageUpscalerFile(model.id, model.fileName)
            else ->
                modelRepo.modelFile(model.id, model.fileName)
        }
        destFile.parentFile?.mkdirs()

        val hxdId = HxdManager.enqueue(context, model.fileUri, destFile.absolutePath)
        downloadCoordinator.registerLabel(hxdId, model.name, downloadTypeOf(model))
        _downloadIds.value = _downloadIds.value + (model.id to hxdId)
        _downloadStates.value = _downloadStates.value + (model.id to
            HxdState(hxdId, model.fileUri, destFile.absolutePath, 0L, -1L, 0L, HxdStatus.QUEUED))

        viewModelScope.launch(Dispatchers.IO) {
            HxdManager.observe(hxdId).collect { state ->
                if (state == null) return@collect
                _downloadStates.value = _downloadStates.value + (model.id to state)

                when (state.status) {
                    HxdStatus.COMPLETED -> {
                        if (model.modelType == "image_gen") {
                            finalizeImageGenDownload(model, destFile)
                        } else if (model.modelType == "image_upscaler") {
                            finalizeImageUpscalerDownload(model, destFile)
                        } else {
                            finalizeNonVlmDownload(model, destFile)
                        }
                    }
                    HxdStatus.FAILED -> {
                        _downloadIds.value = _downloadIds.value - model.id
                    }
                    HxdStatus.CANCELLED -> {
                        _downloadIds.value = _downloadIds.value - model.id
                        _downloadStates.value = _downloadStates.value - model.id
                    }
                    else -> {}
                }
            }
        }
    }



    private fun finalizeImageGenDownload(model: HuggingFaceModel, archive: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            _extractingIds.value = _extractingIds.value + model.id
            installProgress.extractStarted(model.id)
            try {
                if (!archive.exists()) {
                    _error.value = "Downloaded archive missing for ${model.name}"
                    return@launch
                }
                val targetDir = modelRepo.imageModelDir(model.id)
                targetDir.listFiles()?.forEach { it.deleteRecursively() }
                val ok = unzipInto(archive, targetDir) { entryName ->
                    _extractingFile.update { it + (model.id to entryName) }
                }
                if (!ok) {
                    _error.value = "Extraction failed for ${model.name}"
                    targetDir.deleteRecursively()
                    return@launch
                }
                archive.delete()
                // Walk down through any chain of single-subdir wrappers (xororz/sd-qnn ZIPs
                // wrap their contents in `output_<size>/qnn_models_<bucket>/`) until we find
                // the dir that actually contains unet.bin / clip*.mnn / tokenizer.json.
                val effectiveDir = liftToModelDir(targetDir)
                val folderSize = effectiveDir.walkTopDown()
                    .filter { it.isFile }
                    .sumOf { it.length() }
                modelRepo.insert(
                    ModelInfo(
                        id = model.id, name = model.name,
                        path = effectiveDir.absolutePath, pathType = PathType.FILE,
                        providerType = ProviderType.IMAGE_GEN,
                        fileSize = folderSize,
                    ),
                )
            } catch (t: Throwable) {
                android.util.Log.e("ModelStoreVM", "finalizeImageGenDownload threw", t)
                _error.value = "Extraction error: ${t.message}"
            } finally {
                _extractingIds.value = _extractingIds.value - model.id
                _extractingFile.value = _extractingFile.value - model.id
                _downloadIds.value = _downloadIds.value - model.id
                _downloadStates.value = _downloadStates.value - model.id
                installProgress.extractFinished(model.id)
            }
        }
    }

    private fun finalizeImageUpscalerDownload(model: HuggingFaceModel, destFile: java.io.File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!destFile.exists()) {
                    _error.value = "Downloaded file missing for ${model.name}"
                    return@launch
                }
                modelRepo.insert(
                    ModelInfo(
                        id = model.id, name = model.name,
                        path = destFile.absolutePath, pathType = PathType.FILE,
                        providerType = ProviderType.IMAGE_UPSCALER,
                        fileSize = if (model.sizeBytes > 0) model.sizeBytes else destFile.length(),
                    ),
                )
            } finally {
                _downloadIds.value = _downloadIds.value - model.id
                _downloadStates.value = _downloadStates.value - model.id
            }
        }
    }

    private fun liftToModelDir(root: java.io.File): java.io.File {
        val signals = setOf(
            "unet.bin", "unet.mnn", "clip.mnn", "clip_v2.mnn",
            "vae_decoder.bin", "vae_decoder.mnn", "tokenizer.json",
        )
        var cur = root
        repeat(6) {
            val files = cur.listFiles()?.toList().orEmpty()
            val hasModelFile = files.any { it.isFile && it.name in signals }
            if (hasModelFile) return cur
            val onlyDir = files.singleOrNull { it.isDirectory && !it.name.startsWith(".") }
                ?: return cur
            cur = onlyDir
        }
        return cur
    }

    private fun unzipInto(
        archive: java.io.File,
        target: java.io.File,
        onEntry: (String) -> Unit,
    ): Boolean {
        val targetCanonical = target.canonicalPath + java.io.File.separator
        val workers = minOf(4, Runtime.getRuntime().availableProcessors().coerceAtLeast(1))
        val bufSize = 256 * 1024

        return java.util.zip.ZipFile(archive).use { zip ->
            val fileEntries = ArrayList<java.util.zip.ZipEntry>()
            val it = zip.entries()
            while (it.hasMoreElements()) {
                val e = it.nextElement()
                val raw = e.name
                if (raw.contains("..")) continue
                val outFile = java.io.File(target, raw)
                val canonical = outFile.canonicalPath
                if (!canonical.startsWith(targetCanonical) && canonical != target.canonicalPath) {
                    return@use false
                }
                if (e.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    fileEntries.add(e)
                }
            }

            val executor = java.util.concurrent.Executors.newFixedThreadPool(workers)
            try {
                val futures = fileEntries.map { entry ->
                    executor.submit {
                        val outFile = java.io.File(target, entry.name)
                        onEntry(entry.name.substringAfterLast('/'))
                        zip.getInputStream(entry).use { input ->
                            java.io.BufferedOutputStream(
                                java.io.FileOutputStream(outFile),
                                bufSize,
                            ).use { out -> input.copyTo(out, bufSize) }
                        }
                    }
                }
                for (f in futures) {
                    try {
                        f.get()
                    } catch (ee: java.util.concurrent.ExecutionException) {
                        throw ee.cause ?: ee
                    }
                }
            } finally {
                executor.shutdown()
            }
            true
        }
    }

    private fun finalizeNonVlmDownload(model: HuggingFaceModel, destFile: java.io.File) {
        val provider = when (model.modelType) {
            "tts" -> ProviderType.TTS
            "stt" -> ProviderType.STT
            "embedding" -> ProviderType.EMBEDDING
            else -> ProviderType.GGUF
        }
        modelRepo.insert(ModelInfo(
            id = model.id, name = model.name,
            path = destFile.absolutePath, pathType = PathType.FILE,
            providerType = provider,
            fileSize = if (model.sizeBytes > 0) model.sizeBytes else destFile.length(),
        ))
        if (provider == ProviderType.GGUF &&
            modelRepo.models.value.none { it.providerType == ProviderType.GGUF && it.isActive && it.id != model.id }
        ) {
            modelRepo.setActive(model.id)
        }
        _downloadIds.value = _downloadIds.value - model.id
        _downloadStates.value = _downloadStates.value - model.id
    }



    private fun downloadTypeOf(model: HuggingFaceModel): String = when {
        model.modelType.isNotBlank() -> model.modelType
        else -> "gguf"
    }

    fun cancelDownload(modelId: String) {
        _downloadIds.value[modelId]?.let { HxdManager.cancel(it) }
        _downloadIds.value = _downloadIds.value - modelId
        _downloadStates.value = _downloadStates.value - modelId
    }


    fun deleteModel(model: ModelInfo) {
        viewModelScope.launch(Dispatchers.IO) {
            _deleteInProgress.value = model.id
            try {
                if (model.pathType == PathType.FILE) java.io.File(model.path).delete()
                modelRepo.delete(model.id)
            } finally {
                _deleteInProgress.value = null
            }
        }
    }

    fun loadModel(model: ModelInfo) {
        viewModelScope.launch {
            val config = modelRepo.getConfig(model.id)
            val configJson = buildConfigJson(config)
            when (model.pathType) {
                PathType.FILE -> InferenceClient.loadModel(model.path, configJson)
                PathType.CONTENT_URI -> InferenceClient.loadModelFromUri(context, Uri.parse(model.path), configJson)
            }
            modelRepo.setActive(model.id)
        }
    }

    fun unloadModel() {
        viewModelScope.launch { InferenceClient.unloadModel() }
    }

    fun importLocalModel(
        uri: Uri,
        fileName: String,
        fileSize: Long,
        providerType: ProviderType = ProviderType.GGUF,
    ) {
        modelRepo.insert(ModelInfo(
            id = checksumId(uri.toString(), fileName, fileSize),
            name = fileName.removeSuffix(".gguf"),
            path = uri.toString(), pathType = PathType.CONTENT_URI,
            providerType = providerType, fileSize = fileSize,
        ))
    }

    suspend fun getModelConfig(modelId: String): ModelConfig? = modelRepo.getConfig(modelId)

    fun saveModelConfig(config: ModelConfig) {
        modelRepo.updateConfig(config)
        val info = installedModels.value.firstOrNull { it.id == config.modelId } ?: return
        if (info.providerType != ProviderType.GGUF) return
        viewModelScope.launch { modelSession.load(info) }
    }


    fun addRepository(repo: HFRepository) {
        viewModelScope.launch { repoDataStore.addRepository(repo); loadModels() }
    }

    fun removeRepository(repoId: String) {
        viewModelScope.launch { repoDataStore.removeRepository(repoId); loadModels() }
    }

    fun toggleRepository(repoId: String) {
        viewModelScope.launch { repoDataStore.toggleRepository(repoId); loadModels() }
    }

    fun updateRepository(repo: HFRepository) {
        viewModelScope.launch { repoDataStore.updateRepository(repo); loadModels() }
    }

    fun validateRepository(repo: HFRepository) {
        viewModelScope.launch {
            _validationResults.value += repo.id to ValidationResult.Checking
            _validationResults.value += repo.id to validator.validate(repo)
        }
    }


    fun setExplorerQuery(query: String) {
        _explorerQuery.value = query
        if (query.isBlank()) { _explorerResults.value = emptyList(); _explorerError.value = null }
    }

    fun searchExplorerRepositories() {
        explorerSearchJob?.cancel()
        explorerSearchJob = viewModelScope.launch {
            val q = _explorerQuery.value.trim()
            if (q.isBlank()) { _explorerError.value = "Enter a search term"; return@launch }
            _isExplorerLoading.value = true; _explorerError.value = null
            try {
                explorer.searchGgufRepos(q).onSuccess { repos ->
                    _explorerResults.value = repos
                    if (repos.isEmpty()) _explorerError.value = "No repositories found"
                }.onFailure { _explorerResults.value = emptyList(); _explorerError.value = it.message }
            } finally { _isExplorerLoading.value = false }
        }
    }

    fun addExplorerRepository(repo: ExplorerRepo) {
        viewModelScope.launch {
            val existing = repoDataStore.repositories.value
            if (existing.any { it.repoPath.equals(repo.id, ignoreCase = true) }) {
                _explorerError.value = "Repository already added"; return@launch
            }
            repoDataStore.addRepository(HFRepository(
                id = "hf-${repo.id.replace("/", "-").lowercase()}",
                name = repo.id.substringAfter("/"),
                repoPath = repo.id,
            ))
            _explorerError.value = null
            loadModels()
        }
    }


    private fun buildConfigJson(config: ModelConfig?): String {
        if (config == null) return "{}"
        val sb = StringBuilder(256).append('{')
        val loading = config.loadingParamsJson
        val inference = config.inferenceParamsJson
        if (loading != "{}" && loading.isNotBlank()) {
            val inner = loading.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner).append(',')
        }
        if (inference != "{}" && inference.isNotBlank()) {
            val inner = inference.trim().removePrefix("{").removeSuffix("}")
            if (inner.isNotBlank()) sb.append(inner)
        }
        if (sb.last() == ',') sb.deleteCharAt(sb.length - 1)
        sb.append('}')
        return sb.toString()
    }

    private fun checksumId(uri: String, name: String, size: Long): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(uri.toByteArray()); digest.update(name.toByteArray()); digest.update(size.toString().toByteArray())
        return digest.digest().take(16).joinToString("") { "%02x".format(it) }
    }
}

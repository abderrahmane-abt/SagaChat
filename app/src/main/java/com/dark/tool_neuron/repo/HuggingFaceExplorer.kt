package com.dark.tool_neuron.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class ExplorerRepo(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val gated: Boolean,
    val tags: List<String> = emptyList(),
    val pipelineTag: String? = null,
    val lastModified: String? = null,
)

data class RepoFile(
    val path: String,
    val sizeBytes: Long,
)

data class HfRepoDetail(
    val id: String,
    val author: String,
    val downloads: Long,
    val likes: Long,
    val gated: Boolean,
    val files: List<RepoFile>,
) {
    val ggufFiles: List<RepoFile>
        get() = files.filter { it.path.endsWith(".gguf", ignoreCase = true) }

    val totalGgufBytes: Long
        get() = ggufFiles.sumOf { it.sizeBytes }
}

@Singleton
class HuggingFaceExplorer @Inject constructor() {

    suspend fun searchModels(
        query: String,
        filters: HfFilters = HfFilters(),
        limit: Int = 30,
    ): Result<List<ExplorerRepo>> = withContext(Dispatchers.IO) {
        HuggingFaceApi.fetchJsonArrayResult(HuggingFaceApi.searchUrl(query, filters, limit))
            .map { arr ->
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.getJSONObject(i)
                    val id = obj.optString("id", "")
                    if (id.isBlank() || !id.contains("/")) return@mapNotNull null
                    val tagsArr = obj.optJSONArray("tags")
                    val tags: List<String> = if (tagsArr != null) {
                        (0 until tagsArr.length()).map { tagsArr.optString(it) }
                            .filter { it.isNotBlank() }
                    } else emptyList()
                    ExplorerRepo(
                        id = id,
                        author = obj.optString("author", id.substringBefore("/")),
                        downloads = obj.optLong("downloads", 0),
                        likes = obj.optLong("likes", 0),
                        gated = obj.opt("gated")?.let { it != false && it.toString() != "false" } ?: false,
                        tags = tags,
                        pipelineTag = obj.optString("pipeline_tag", "").takeIf { it.isNotBlank() },
                        lastModified = obj.optString("lastModified", "").takeIf { it.isNotBlank() },
                    )
                }.distinctBy { it.id }
            }
    }

    suspend fun searchGgufRepos(query: String, limit: Int = 30): Result<List<ExplorerRepo>> =
        searchModels(query, HfFilters(libraries = setOf("gguf")), limit)

    suspend fun fetchRepoDetail(repoPath: String): Result<HfRepoDetail> =
        withContext(Dispatchers.IO) {
            runCatching {
                val info = HuggingFaceApi.fetchJson(HuggingFaceApi.modelInfoUrl(repoPath))
                    ?: error("repository not found")
                val tree = HuggingFaceApi.fetchJsonArray(HuggingFaceApi.modelTreeUrl(repoPath))
                    ?: error("failed to list files")
                val files = (0 until tree.length()).mapNotNull { i ->
                    val o = tree.optJSONObject(i) ?: return@mapNotNull null
                    val path = o.optString("path", "")
                    if (path.isBlank()) return@mapNotNull null
                    RepoFile(path = path, sizeBytes = o.optLong("size", 0L))
                }
                val gatedRaw = info.opt("gated")
                HfRepoDetail(
                    id = repoPath,
                    author = info.optString("author", repoPath.substringBefore("/")),
                    downloads = info.optLong("downloads", 0),
                    likes = info.optLong("likes", 0),
                    gated = gatedRaw != null && gatedRaw != false && gatedRaw.toString() != "false",
                    files = files,
                )
            }
        }
}

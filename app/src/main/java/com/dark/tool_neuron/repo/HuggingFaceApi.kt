package com.dark.tool_neuron.repo

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object HuggingFaceApi {
    private const val BASE = "https://huggingface.co"
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 15_000

    fun modelInfoUrl(repoPath: String): String =
        "$BASE/api/models/$repoPath"

    fun modelTreeUrl(repoPath: String, recursive: Boolean = true): String =
        "$BASE/api/models/$repoPath/tree/main" + if (recursive) "?recursive=true" else ""

    fun resolveFileUrl(repoPath: String, filePath: String): String =
        "$BASE/$repoPath/resolve/main/$filePath"

    fun searchUrl(query: String, filters: HfFilters, limit: Int): String {
        val params = mutableListOf<String>()
        if (query.isNotBlank()) params += "search=${enc(query.trim())}"
        if (filters.author.isNotBlank()) params += "author=${enc(filters.author.trim())}"

        filters.pipelineTag?.takeIf { it.isNotBlank() }?.let {
            params += "pipeline_tag=${enc(it)}"
        }

        val tagFilters = mutableListOf<String>()
        tagFilters += filters.libraries
        tagFilters += filters.languages
        tagFilters += filters.licenses
        tagFilters += filters.otherTags
        tagFilters += filters.quantTags
        filters.regions.forEach { tagFilters += "region:$it" }
        if (filters.trainedDataset.isNotBlank()) {
            tagFilters += "dataset:${filters.trainedDataset.trim()}"
        }
        tagFilters.forEach { params += "filter=${enc(it)}" }

        if (filters.apps.isNotEmpty()) {
            params += "apps=${filters.apps.joinToString(",") { enc(it) }}"
        }
        if (filters.providers.isNotEmpty()) {
            params += "inference_provider=${filters.providers.joinToString(",") { enc(it) }}"
        }

        when (filters.gated) {
            GatedFilter.ONLY_GATED -> params += "gated=true"
            GatedFilter.ONLY_OPEN -> params += "gated=false"
            GatedFilter.ANY -> {}
        }
        if (filters.inferenceWarm) params += "inference=warm"

        val pmin = filters.paramsMinMillions
        val pmax = filters.paramsMaxMillions
        if (pmin > 0L || pmax > 0L) {
            val parts = mutableListOf<String>()
            if (pmin > 0L) parts += "min:${formatParams(pmin)}"
            if (pmax > 0L) parts += "max:${formatParams(pmax)}"
            params += "num_parameters=${enc(parts.joinToString(","))}"
        }

        params += "sort=${filters.sort.apiKey}"
        params += "limit=$limit"
        params += "expand=tags"
        params += "expand=downloads"
        params += "expand=likes"
        params += "expand=gated"
        params += "expand=author"
        params += "expand=lastModified"
        params += "expand=pipeline_tag"

        return "$BASE/api/models?${params.joinToString("&")}"
    }

    fun fetchJson(urlStr: String): JSONObject? = withConnection(urlStr) { conn ->
        if (conn.responseCode != 200) null
        else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
    }

    fun fetchJsonArray(urlStr: String): JSONArray? = withConnection(urlStr) { conn ->
        if (conn.responseCode != 200) null
        else JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
    }

    fun fetchJsonArrayResult(urlStr: String): Result<JSONArray> = runCatching {
        val conn = openConnection(urlStr)
        try {
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
        } finally { conn.disconnect() }
    }

    fun headStatus(urlStr: String): Int? = withConnection(urlStr, accept = false) { conn ->
        try { conn.responseCode } catch (_: Exception) { null }
    }

    private fun openConnection(urlStr: String, accept: Boolean = true): HttpURLConnection =
        (URL(urlStr).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            if (accept) setRequestProperty("Accept", "application/json")
        }

    private inline fun <T> withConnection(
        urlStr: String,
        accept: Boolean = true,
        block: (HttpURLConnection) -> T,
    ): T? {
        val conn = openConnection(urlStr, accept)
        return try { block(conn) } catch (_: Exception) { null } finally { conn.disconnect() }
    }

    private fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

    private fun formatParams(millions: Long): String = when {
        millions >= 1_000L -> "${millions / 1_000L}B"
        else -> "${millions}M"
    }
}

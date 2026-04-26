package com.dark.tool_neuron.repo

enum class HfSort(val apiKey: String, val label: String) {
    TRENDING("trending_score", "Trending"),
    DOWNLOADS("downloads", "Downloads"),
    LIKES("likes", "Likes"),
    RECENT_UPDATED("last_modified", "Recently updated"),
    RECENT_CREATED("created_at", "Recently created"),
}

enum class GatedFilter(val label: String) {
    ANY("Any"),
    ONLY_OPEN("Open only"),
    ONLY_GATED("Gated only"),
}

data class HfFilters(
    val pipelineTag: String? = null,
    val libraries: Set<String> = setOf("gguf"),
    val apps: Set<String> = emptySet(),
    val providers: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val licenses: Set<String> = emptySet(),
    val regions: Set<String> = emptySet(),
    val otherTags: Set<String> = emptySet(),
    val quantTags: Set<String> = emptySet(),
    val trainedDataset: String = "",
    val author: String = "",
    val gated: GatedFilter = GatedFilter.ANY,
    val inferenceWarm: Boolean = false,
    val paramsMinMillions: Long = 0L,
    val paramsMaxMillions: Long = 0L,
    val sort: HfSort = HfSort.TRENDING,
) {
    val activeCount: Int
        get() {
            var n = 0
            if (pipelineTag != null) n++
            n += libraries.size
            n += apps.size
            n += providers.size
            n += languages.size
            n += licenses.size
            n += regions.size
            n += otherTags.size
            n += quantTags.size
            if (trainedDataset.isNotBlank()) n++
            if (author.isNotBlank()) n++
            if (gated != GatedFilter.ANY) n++
            if (inferenceWarm) n++
            if (paramsMinMillions > 0L || paramsMaxMillions > 0L) n++
            return n
        }
}

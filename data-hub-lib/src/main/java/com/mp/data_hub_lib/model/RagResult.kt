package com.mp.data_hub_lib.model

data class RagResult(
    val docs: List<Doc>,
    val stats: GenerationStats
)
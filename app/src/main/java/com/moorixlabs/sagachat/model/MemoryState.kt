package com.moorixlabs.sagachat.model

data class MemoryState(
    val characterId: String,
    val summary: String = "",
    val entityJson: String = "{}",
    val lastSummarizedTurnIndex: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
)

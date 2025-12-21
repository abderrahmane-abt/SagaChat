package com.mp.user_data.models

/**
 * Statistics about the brain's contents.
 */
data class BrainStats(
    val totalNodes: Int,
    val chatCount: Int,
    val memoryCount: Int,
    val logCount: Int
)

/**
 * Exception thrown when brain operations fail.
 */
class BrainException(message: String, cause: Throwable? = null) : Exception(message, cause)
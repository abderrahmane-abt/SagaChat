package com.dark.neuroverse.model

data class DownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val isComplete: Boolean = false,
    val errorMessage: String? = null
)
package com.moorixlabs.sagachat.model

sealed interface DownloadProgress {
    data object Indeterminate : DownloadProgress
    data class Determinate(val fraction: Float) : DownloadProgress
}

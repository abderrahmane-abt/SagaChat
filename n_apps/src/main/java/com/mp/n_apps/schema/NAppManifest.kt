package com.mp.n_apps.schema

import kotlinx.serialization.Serializable

@Serializable
data class NAppManifest(
    val nappVersion: String = "1.0",
    val app: AppInfo = AppInfo()
)

@Serializable
data class AppInfo(
    val id: String = "untitled",
    val name: String = "Untitled NApp",
    val version: String = "1.0.0",
    val description: String = "",
    val author: String = ""
)

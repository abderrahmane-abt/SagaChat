package com.mp.data_hub_lib.model

import kotlinx.serialization.Serializable

@Serializable
data class DataSetManifest(
    val name: String,
    val description: String,
    val vecxPasswordHint: String,
    val created: String,
    val author: String
)
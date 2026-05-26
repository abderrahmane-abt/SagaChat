package com.moorixlabs.sagachat.model

import com.moorixlabs.sagachat.model.enums.PathType
import com.moorixlabs.sagachat.model.enums.ProviderType

data class ModelInfo(
    val id: String,
    val name: String,
    val path: String,
    val pathType: PathType,
    val providerType: ProviderType = ProviderType.GGUF,
    val fileSize: Long = 0,
    val isActive: Boolean = false,
)

data class ModelConfig(
    val id: String,
    val modelId: String,
    val loadingParamsJson: String = "{}",
    val inferenceParamsJson: String = "{}",
)

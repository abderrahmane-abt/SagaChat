package com.dark.tool_neuron.models.table_schema

import com.dark.tool_neuron.models.engine_schema.GgufEngineSchema
import com.dark.tool_neuron.models.enums.ProviderType

data class ModelWithConfig(
    val model: Model,
    val config: ModelConfig?
)

fun Model.toGgufSchema(): GgufEngineSchema? {
    if (providerType != ProviderType.GGUF) return null
    return GgufEngineSchema()
}

fun ModelWithConfig.toGgufSchema(): GgufEngineSchema? {
    if (model.providerType != ProviderType.GGUF) return null
    return GgufEngineSchema.fromJson(
        config?.modelLoadingParams,
        config?.modelInferenceParams
    )
}

fun GgufEngineSchema.toModelConfig(modelId: String): ModelConfig {
    return ModelConfig(
        modelId = modelId,
        modelLoadingParams = toLoadingJson(),
        modelInferenceParams = toInferenceJson()
    )
}
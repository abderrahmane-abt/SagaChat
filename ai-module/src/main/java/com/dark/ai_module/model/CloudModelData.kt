package com.dark.ai_module.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import java.io.File

// ============================================================================
// CLOUD MODEL (Server Response)
// ============================================================================

@Serializable
data class CloudModel(
    val modelName: String,
    val modelDescription: String,
    val providerName: String,
    val modelType: ModelType,
    val modelFileSize: String,
    @SerialName("meta-data") val metaData: Map<String, String> = emptyMap()
)

@Serializable
enum class ModelType {
    TEXT, VLM, EMBEDDING, TTS, STT, IMAGE_GEN
}

// ============================================================================
// LOCAL MODEL (Room DB Storage)
// ============================================================================

@Serializable
data class LocalModelStub(
    val modelId: String,
    val modelName: String,
    val modelDescription: String,
    val modelFileSize: String,
    val providerName: String,
    val modelType: ModelType,
    val modelDir: String,
    val configJson: String,
    val createdAt: Long = System.currentTimeMillis()
)

// ============================================================================
// PROVIDER MODELS
// ============================================================================

sealed class ProviderModel {
    abstract val modelName: String
    abstract val modelDescription: String
    abstract val modelFileSize: String
    abstract val modelPath: String
}

@Serializable
data class GGUFModel(
    override val modelName: String,
    override val modelDescription: String,
    override val modelFileSize: String,
    override val modelPath: String,
    val config: GGUFConfig
) : ProviderModel()

@Serializable
data class SherpaTTSModel(
    override val modelName: String,
    override val modelDescription: String,
    override val modelFileSize: String,
    override val modelPath: String,
    val config: SherpaConfig
) : ProviderModel()

@Serializable
data class SherpaSTTModel(
    override val modelName: String,
    override val modelDescription: String,
    override val modelFileSize: String,
    override val modelPath: String,
    val config: SherpaConfig
) : ProviderModel()

@Serializable
data class OpenRouterModel(
    override val modelName: String,
    override val modelDescription: String,
    override val modelFileSize: String,
    override val modelPath: String = "",
    val config: APIConfig
) : ProviderModel()

// ============================================================================
// CONFIGURATIONS
// ============================================================================

@Serializable
data class GGUFConfig(
    val architecture: String = "LLAMA",
    val ctxSize: Int = 4096,
    val gpuLayers: Int = 0,
    val maxTokens: Int = 2048,
    val seed: Int = -1,
    val threads: Int = 4,
    val useMMAP: Boolean = true,
    val useMLOCK: Boolean = false,
    val minP: Double = 1.0,
    val mirostat: Int = 0,
    val mirostatEta: Double = 0.1,
    val mirostatTau: Double = 5.0,
    val temp: Double = 0.7,
    val topK: Int = 20,
    val topP: Double = 0.5,
    val modelTags: List<String> = emptyList(),
    val systemPrompt: String = "",
    val chatTemplate: String = ""
)

@Serializable
data class SherpaConfig(
    val encoder: String = "",
    val decoder: String = "",
    val tokens: String = "",
    val voices: String = "",
    val dataDir: String = "",
    val modelDir: String = ""
)

@Serializable
data class APIConfig(
    val endpoint: String,
    val modelId: String,
    val maxTokens: Int = 2048,
    val temperature: Double = 0.7,
    val topP: Double = 0.9,
    val frequencyPenalty: Double = 0.0,
    val presencePenalty: Double = 0.0
)

// ============================================================================
// PROVIDER INTERFACE
// ============================================================================

interface ModelProvider {
    val providerName: String
    fun supports(cloudModel: CloudModel): Boolean
    fun convertToLocal(cloudModel: CloudModel, baseDir: File): LocalModelStub
    fun deserializeToModel(localModel: LocalModelStub): ProviderModel
}

// ============================================================================
// PROVIDER IMPLEMENTATIONS
// ============================================================================

class GGUFProvider : ModelProvider {
    override val providerName: String = "GGUF"

    private val json = Json { ignoreUnknownKeys = true }

    override fun supports(cloudModel: CloudModel): Boolean {
        return cloudModel.providerName.contains("GGUF", ignoreCase = true) &&
                cloudModel.modelType in listOf(ModelType.TEXT, ModelType.VLM)
    }

    override fun convertToLocal(cloudModel: CloudModel, baseDir: File): LocalModelStub {
        val meta = cloudModel.metaData
        val config = GGUFConfig(
            architecture = meta.getString("architecture", "LLAMA"),
            ctxSize = meta.getInt("ctxSize", 4096),
            gpuLayers = meta.getInt("gpu-layers", 0),
            maxTokens = meta.getInt("maxTokens", 2048),
            seed = meta.getInt("seed", -1),
            threads = meta.getInt("threads", 4),
            useMMAP = meta.getBoolean("useMMAP", true),
            useMLOCK = meta.getBoolean("useMLOCK", false),
            minP = meta.getDouble("min-p", 1.0),
            mirostat = meta.getInt("mirostat", 0),
            mirostatEta = meta.getDouble("mirostatEta", 0.1),
            mirostatTau = meta.getDouble("mirostatTau", 5.0),
            temp = meta.getDouble("temp", 0.7),
            topK = meta.getInt("topK", 20),
            topP = meta.getDouble("topP", 0.5),
            modelTags = meta["modelTags"]?.split(",")?.map { it.trim() } ?: emptyList(),
            systemPrompt = meta.getString("systemPrompt"),
            chatTemplate = meta.getString("chatTemplate")
        )

        val modelDir = File(baseDir, cloudModel.modelName)

        return LocalModelStub(
            modelId = generateModelId(cloudModel),
            modelName = cloudModel.modelName,
            modelDescription = cloudModel.modelDescription,
            modelFileSize = cloudModel.modelFileSize,
            providerName = providerName,
            modelType = cloudModel.modelType,
            modelDir = modelDir.absolutePath,
            configJson = json.encodeToString(serializer(), config)
        )
    }

    override fun deserializeToModel(localModel: LocalModelStub): ProviderModel {
        val config = json.decodeFromString<GGUFConfig>(localModel.configJson)
        val modelPath = File(localModel.modelDir, "${localModel.modelName}.gguf").absolutePath

        return GGUFModel(
            modelName = localModel.modelName,
            modelDescription = localModel.modelDescription,
            modelFileSize = localModel.modelFileSize,
            modelPath = modelPath,
            config = config
        )
    }
}

class SherpaTTSProvider : ModelProvider {
    override val providerName: String = "SHERPA-ONNX-TTS"

    private val json = Json { ignoreUnknownKeys = true }

    override fun supports(cloudModel: CloudModel): Boolean {
        return cloudModel.providerName.contains("SHERPA-ONNX", ignoreCase = true) &&
                cloudModel.modelType == ModelType.TTS
    }

    override fun convertToLocal(cloudModel: CloudModel, baseDir: File): LocalModelStub {
        val meta = cloudModel.metaData
        val config = SherpaConfig(
            encoder = meta.getString("encoder"),
            decoder = meta.getString("decoder"),
            tokens = meta.getString("tokens-txt"),
            voices = meta.getString("voices"),
            dataDir = meta.getString("dataDir"),
            modelDir = meta.getString("modelDir")
        )

        val modelDir = File(baseDir, cloudModel.modelName)

        return LocalModelStub(
            modelId = generateModelId(cloudModel),
            modelName = cloudModel.modelName,
            modelDescription = cloudModel.modelDescription,
            modelFileSize = cloudModel.modelFileSize,
            providerName = providerName,
            modelType = cloudModel.modelType,
            modelDir = modelDir.absolutePath,
            configJson = json.encodeToString(serializer(), config)
        )
    }

    override fun deserializeToModel(localModel: LocalModelStub): ProviderModel {
        val config = json.decodeFromString<SherpaConfig>(localModel.configJson)

        return SherpaTTSModel(
            modelName = localModel.modelName,
            modelDescription = localModel.modelDescription,
            modelFileSize = localModel.modelFileSize,
            modelPath = localModel.modelDir,
            config = config
        )
    }
}

class SherpaSTTProvider : ModelProvider {
    override val providerName: String = "SHERPA-ONNX-STT"

    private val json = Json { ignoreUnknownKeys = true }

    override fun supports(cloudModel: CloudModel): Boolean {
        return cloudModel.providerName.contains("SHERPA-ONNX", ignoreCase = true) &&
                cloudModel.modelType == ModelType.STT
    }

    override fun convertToLocal(cloudModel: CloudModel, baseDir: File): LocalModelStub {
        val meta = cloudModel.metaData
        val config = SherpaConfig(
            encoder = meta.getString("encoder"),
            decoder = meta.getString("decoder"),
            tokens = meta.getString("tokens-txt"),
            voices = meta.getString("voices"),
            dataDir = meta.getString("dataDir"),
            modelDir = meta.getString("modelDir")
        )

        val modelDir = File(baseDir, cloudModel.modelName)

        return LocalModelStub(
            modelId = generateModelId(cloudModel),
            modelName = cloudModel.modelName,
            modelDescription = cloudModel.modelDescription,
            modelFileSize = cloudModel.modelFileSize,
            providerName = providerName,
            modelType = cloudModel.modelType,
            modelDir = modelDir.absolutePath,
            configJson = json.encodeToString(serializer(), config)
        )
    }

    override fun deserializeToModel(localModel: LocalModelStub): ProviderModel {
        val config = json.decodeFromString<SherpaConfig>(localModel.configJson)

        return SherpaSTTModel(
            modelName = localModel.modelName,
            modelDescription = localModel.modelDescription,
            modelFileSize = localModel.modelFileSize,
            modelPath = localModel.modelDir,
            config = config
        )
    }
}

class OpenRouterProvider : ModelProvider {
    override val providerName: String = "OpenRouter"

    private val json = Json { ignoreUnknownKeys = true }

    override fun supports(cloudModel: CloudModel): Boolean {
        return cloudModel.providerName.contains("OpenRouter", ignoreCase = true)
    }

    override fun convertToLocal(cloudModel: CloudModel, baseDir: File): LocalModelStub {
        val meta = cloudModel.metaData
        val config = APIConfig(
            endpoint = meta.getString("apiEndpoint", "https://openrouter.ai/api/v1"),
            modelId = meta.getString("modelId", cloudModel.modelName),
            maxTokens = meta.getInt("maxTokens", 2048),
            temperature = meta.getDouble("temperature", 0.7),
            topP = meta.getDouble("topP", 0.9),
            frequencyPenalty = meta.getDouble("frequencyPenalty", 0.0),
            presencePenalty = meta.getDouble("presencePenalty", 0.0)
        )

        return LocalModelStub(
            modelId = generateModelId(cloudModel),
            modelName = cloudModel.modelName,
            modelDescription = cloudModel.modelDescription,
            modelFileSize = cloudModel.modelFileSize,
            providerName = providerName,
            modelType = cloudModel.modelType,
            modelDir = "",
            configJson = json.encodeToString(serializer(), config)
        )
    }

    override fun deserializeToModel(localModel: LocalModelStub): ProviderModel {
        val config = json.decodeFromString<APIConfig>(localModel.configJson)

        return OpenRouterModel(
            modelName = localModel.modelName,
            modelDescription = localModel.modelDescription,
            modelFileSize = localModel.modelFileSize,
            config = config
        )
    }
}

// ============================================================================
// PROVIDER MANAGER
// ============================================================================

class ProviderManager(
    private val baseDir: File,
    private val providers: List<ModelProvider> = listOf(
        GGUFProvider(),
        SherpaTTSProvider(),
        SherpaSTTProvider(),
        OpenRouterProvider()
    )
) {

    /**
     * Convert CloudModel to LocalModelStub for Room DB storage
     */
    fun convertToLocal(cloudModel: CloudModel): LocalModelStub {
        val provider = findProvider(cloudModel)
            ?: throw UnsupportedOperationException(
                "No provider found for: ${cloudModel.providerName} with type: ${cloudModel.modelType}"
            )

        return provider.convertToLocal(cloudModel, baseDir)
    }

    /**
     * Deserialize LocalModelStub from Room DB to usable ProviderModel
     */
    fun deserializeToModel(localModel: LocalModelStub): ProviderModel {
        val provider = providers.find { it.providerName == localModel.providerName }
            ?: throw IllegalStateException(
                "Provider not found: ${localModel.providerName}"
            )

        return provider.deserializeToModel(localModel)
    }

    /**
     * Batch convert multiple CloudModels
     */
    fun convertBatch(cloudModels: List<CloudModel>): List<LocalModelStub> {
        return cloudModels.map { convertToLocal(it) }
    }

    /**
     * Register a new provider dynamically
     */
    fun registerProvider(provider: ModelProvider): ProviderManager {
        return ProviderManager(baseDir, providers + provider)
    }

    /**
     * Get all supported providers
     */
    fun getSupportedProviders(): List<String> {
        return providers.map { it.providerName }
    }

    private fun findProvider(cloudModel: CloudModel): ModelProvider? {
        return providers.find { it.supports(cloudModel) }
    }
}

// ============================================================================
// UTILITY EXTENSIONS
// ============================================================================

private fun Map<String, String>.getInt(key: String, default: Int): Int =
    this[key]?.toIntOrNull() ?: default

private fun Map<String, String>.getDouble(key: String, default: Double): Double =
    this[key]?.toDoubleOrNull() ?: default

private fun Map<String, String>.getBoolean(key: String, default: Boolean): Boolean =
    this[key]?.toBooleanStrictOrNull() ?: default

private fun Map<String, String>.getString(key: String, default: String = ""): String =
    this[key] ?: default

private fun generateModelId(cloudModel: CloudModel): String {
    return "${cloudModel.providerName}-${cloudModel.modelName}-${System.currentTimeMillis()}"
        .replace(" ", "-")
        .lowercase()
}
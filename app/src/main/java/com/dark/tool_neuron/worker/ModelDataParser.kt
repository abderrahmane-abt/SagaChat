package com.dark.tool_neuron.worker

import android.annotation.SuppressLint
import com.dark.tool_neuron.engine.GGUFEngine
import com.dark.tool_neuron.models.enums.ProviderType
import com.dark.tool_neuron.models.table_schema.Model
import com.dark.tool_neuron.models.table_schema.ModelConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/**
 * Handles parsing and loading of different model formats
 */
class ModelDataParser {
    
    suspend fun loadModel(
        model: Model,
        config: ModelConfig?
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        return@withContext when (model.providerType) {
            ProviderType.GGUF -> loadGGUFModel(model, config)
            // ProviderType.DIFFUSION -> loadDiffusionModel(model, config)
            // ProviderType.ONNX -> loadONNXModel(model, config)
            else -> ModelLoadResult.Error("Unsupported model type: ${model.providerType}")
        }
    }
    
    private suspend fun loadGGUFModel(
        model: Model,
        config: ModelConfig?
    ): ModelLoadResult = withContext(Dispatchers.IO) {
        try {
            val engine = GGUFEngine()
            val success = engine.load(model, config)
            
            if (success) {
                val infoJson = engine.getModelInfo()
                if (infoJson != null) {
                    val modelInfo = parseGGUFInfo(infoJson)
                    ModelLoadResult.Success(
                        info = modelInfo,
                        engine = engine
                    )
                } else {
                    ModelLoadResult.Error("Failed to retrieve model information")
                }
            } else {
                ModelLoadResult.Error("Failed to load GGUF model")
            }
        } catch (e: Exception) {
            ModelLoadResult.Error("GGUF loading error: ${e.message}")
        }
    }
    
    private fun parseGGUFInfo(jsonString: String): ModelInfo {
        return try {
            val json = JSONObject(jsonString)
            
            GGUFModelInfo(
                providerType = ProviderType.GGUF,
                architecture = json.optString("architecture", "Unknown"),
                name = json.optString("name", "Unnamed Model"),
                description = json.optString("description", ""),
                parameters = mapOf(
                    "Vocabulary Size" to formatNumber(json.optInt("n_vocab", 0)),
                    "Context Length" to formatNumber(json.optInt("n_ctx_train", 0)),
                    "Embedding Dim" to formatNumber(json.optInt("n_embd", 0)),
                    "Layers" to json.optInt("n_layer", 0).toString(),
                    "Attention Heads" to json.optInt("n_head", 0).toString(),
                    "KV Heads" to json.optInt("n_head_kv", 0).toString()
                ),
                vocabularyInfo = if (json.has("vocab_type")) {
                    mapOf(
                        "Type" to json.optString("vocab_type", "").uppercase(),
                        "BOS Token" to json.optInt("bos", 0).toString(),
                        "EOS Token" to json.optInt("eos", 0).toString(),
                        "EOT Token" to json.optInt("eot", 0).toString(),
                        "Newline Token" to json.optInt("nl", 0).toString()
                    )
                } else null,
                systemInfo = json.optString("system", ""),
                chatTemplate = json.optString("chat_template", "")
            )
        } catch (e: Exception) {
            GGUFModelInfo(
                providerType = ProviderType.GGUF,
                architecture = "Error",
                name = "Failed to parse",
                description = e.message ?: "Unknown error"
            )
        }
    }
    
    // Future: Add parsers for other model types
    // private suspend fun loadDiffusionModel(...): ModelLoadResult { ... }
    // private suspend fun loadONNXModel(...): ModelLoadResult { ... }
    
    @SuppressLint("DefaultLocale")
    private fun formatNumber(num: Int): String {
        return when {
            num >= 1_000_000_000 -> String.format("%.2fB", num / 1_000_000_000.0)
            num >= 1_000_000 -> String.format("%.2fM", num / 1_000_000.0)
            num >= 1_000 -> String.format("%.2fK", num / 1_000.0)
            else -> num.toString()
        }
    }
    
    suspend fun unloadModel(engine: Any?) = withContext(Dispatchers.IO) {
        when (engine) {
            is GGUFEngine -> engine.unload()
            // is DiffusionEngine -> engine.unload()
            // Add other engine types
        }
    }

    fun checksumSHA256(modelPath: String): String {
        val file = File(modelPath)
        val digest = MessageDigest.getInstance("SHA-256")

        file.inputStream().use { input ->
            val buffer = ByteArray(8 * 1024)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }

        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

/**
 * Result of model loading operation
 */
sealed class ModelLoadResult {
    data class Success(
        val info: ModelInfo,
        val engine: Any // Can be GGUFEngine, DiffusionEngine, etc.
    ) : ModelLoadResult()
    
    data class Error(val message: String) : ModelLoadResult()
}

/**
 * Base interface for model information
 */
interface ModelInfo {
    val providerType: ProviderType
    val architecture: String
    val name: String
    val description: String
    val parameters: Map<String, String>
    val additionalInfo: Map<String, String>?
}

/**
 * GGUF-specific model information
 */
data class GGUFModelInfo(
    override val providerType: ProviderType,
    override val architecture: String,
    override val name: String,
    override val description: String,
    override val parameters: Map<String, String> = emptyMap(),
    val vocabularyInfo: Map<String, String>? = null,
    val systemInfo: String = "",
    val chatTemplate: String = ""
) : ModelInfo {
    override val additionalInfo: Map<String, String>?
        get() = vocabularyInfo
}

// Future: Add info classes for other model types
// data class DiffusionModelInfo(...) : ModelInfo
// data class ONNXModelInfo(...) : ModelInfo
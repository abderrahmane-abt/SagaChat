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

            // Build parameters map - only existing fields
            val parameters = buildMap {
                if (json.has("n_vocab")) {
                    put("Vocabulary Size", formatNumber(json.getInt("n_vocab")))
                }
                if (json.has("n_ctx_train")) {
                    put("Context Length", formatNumber(json.getInt("n_ctx_train")))
                }
                if (json.has("n_embd")) {
                    put("Embedding Dim", formatNumber(json.getInt("n_embd")))
                }
                if (json.has("n_layer")) {
                    put("Layers", json.getInt("n_layer").toString())
                }
                if (json.has("n_head")) {
                    put("Attention Heads", json.getInt("n_head").toString())
                }
                if (json.has("n_head_kv")) {
                    put("KV Heads", json.getInt("n_head_kv").toString())
                }
            }

            // Build vocabulary info - only existing fields
            val vocabularyInfo = buildMap<String, String> {
                if (json.has("vocab_type")) {
                    put("Type", json.getString("vocab_type").uppercase())
                }
                if (json.has("bos")) {
                    put("BOS Token", json.getInt("bos").toString())
                }
                if (json.has("eos")) {
                    put("EOS Token", json.getInt("eos").toString())
                }
                if (json.has("eot")) {
                    put("EOT Token", json.getInt("eot").toString())
                }
                if (json.has("nl")) {
                    put("Newline Token", json.getInt("nl").toString())
                }
            }.takeIf { it.isNotEmpty() }

            GGUFModelInfo(
                providerType = ProviderType.GGUF,
                architecture = json.optString("architecture"),
                name = json.optString("name"),
                description = json.optString("description"),
                parameters = parameters,
                vocabularyInfo = vocabularyInfo,
                systemInfo = json.optString("system"),
                chatTemplate = json.optString("chat_template"),
                templateType = json.optString("template_type")
            )
        } catch (e: Exception) {
            GGUFModelInfo(
                providerType = ProviderType.GGUF,
                architecture = "",
                name = "",
                description = "Error: ${e.message}"
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
    val chatTemplate: String = "",
    val templateType: String = ""
) : ModelInfo{
    override val additionalInfo: Map<String, String>?
        get() = vocabularyInfo
}

// Future: Add info classes for other model types
// data class DiffusionModelInfo(...) : ModelInfo
// data class ONNXModelInfo(...) : ModelInfo
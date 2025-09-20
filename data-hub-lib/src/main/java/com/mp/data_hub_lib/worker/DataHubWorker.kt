package com.mp.data_hub_lib.worker

import android.content.Context
import com.mp.data_hub_lib.DataNativeLib
import com.mp.data_hub_lib.db.DataHubDAO
import com.mp.data_hub_lib.db.DataHubDatabase
import com.mp.data_hub_lib.db.DataHubDatabaseProvider
import com.mp.data_hub_lib.model.DataHubModel
import com.mp.data_hub_lib.model.DataSetManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class DataHubWorker(
    private val context: Context,
    database: DataHubDatabase = DataHubDatabaseProvider.getDatabase(context),
    val dataNativeLib: DataNativeLib = DataNativeLib()
) {
    private val dataHubDAO: DataHubDAO = database.dataHubDAO()

    // Document data class
    data class Document(
        val id: String,
        val text: String,
        val category: String,
        val embedding: List<Float>
    )

    // Load a data pack
    fun loadPack(packPath: String, password: String, onResult: (Boolean, String?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Use DataNativeLib to extract and load the pack
                val extractedFiles = dataNativeLib.loadPack(packPath, password, context)

                if (extractedFiles != null) {
                    val (_, manifestPath) = extractedFiles
                    val manifest = dataNativeLib.loadManifest(manifestPath)

                    if (manifest != null) {
                        // Create dataset directory in app's files dir
                        val root = File(context.filesDir, "dataHub")
                        if (!root.exists()) root.mkdirs()

                        val datasetDir = File(root, manifest.name.trim())
                        if (!datasetDir.exists()) datasetDir.mkdirs()

                        // Save to database
                        val model = DataHubModel(
                            modelName = manifest.name,
                            modelDescription = manifest.description,
                            modelPath = datasetDir.absolutePath,
                            modelAuthor = manifest.author,
                            modelCreated = manifest.created,
                            isLoaded = true,
                            documentCount = getDocumentCount()
                        )

                        try {
                            dataHubDAO.insertModel(model)
                        } catch (e: Exception) {
                            println("Failed to insert model: ${e.message}")
                        }


                        withContext(Dispatchers.Main) {
                            onResult(true, manifest.name)
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            onResult(false, null)
                        }
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, null)
                    }
                }
            } catch (e: Exception) {
                println("Failed to load pack: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false, null)
                }
            }
        }
    }

    // Get document count from "m" entity
    private fun getDocumentCount(): Int {
        return try {
            val mJson = dataNativeLib.getEntity("m")
            val mArr = JSONArray(mJson)
            mArr.length()
        } catch (e: Exception) {
            0
        }
    }

    // Get all documents for a model
    fun getDocumentsForModel(onResult: (List<Document>?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dJson = dataNativeLib.getEntity("D")
                val dArr = JSONArray(dJson)
                val documents = mutableListOf<Document>()

                for (i in 0 until dArr.length()) {
                    val docObj = dArr.getJSONObject(i)
                    val embeddingArr = docObj.getJSONArray("embedding")
                    val embedding = mutableListOf<Float>()

                    for (j in 0 until embeddingArr.length()) {
                        embedding.add(embeddingArr.getDouble(j).toFloat())
                    }

                    documents.add(Document(
                        id = docObj.getString("id"),
                        text = docObj.getString("text"),
                        category = docObj.optString("category", "Unknown"),
                        embedding = embedding
                    ))
                }

                withContext(Dispatchers.Main) {
                    onResult(documents)
                }
            } catch (e: Exception) {
                println("Failed to get documents: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(null)
                }
            }
        }
    }

    // Search documents using cosine similarity
    fun searchDocuments(queryEmbedding: FloatArray, topK: Int = 5, onResult: (List<Document>?) -> Unit) {
        getDocumentsForModel { documents ->
            if (documents == null) {
                onResult(null)
                return@getDocumentsForModel
            }

            CoroutineScope(Dispatchers.Default).launch {
                val results = documents.map { doc ->
                    val similarity = cosineSimilarity(doc.embedding.toFloatArray(), queryEmbedding)
                    doc to similarity
                }.sortedByDescending { it.second }
                    .take(topK)
                    .map { it.first }

                withContext(Dispatchers.Main) {
                    onResult(results)
                }
            }
        }
    }

    // Cosine similarity calculation
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Double {
        if (a.size != b.size) return 0.0
        val dot = a.zip(b).sumOf { (x, y) -> (x * y).toDouble() }
        val normA = kotlin.math.sqrt(a.sumOf { (it * it).toDouble() })
        val normB = kotlin.math.sqrt(b.sumOf { (it * it).toDouble() })
        return if (normA != 0.0 && normB != 0.0) dot / (normA * normB) else 0.0
    }

    // Database operations
    fun getAllModels(): Flow<List<DataHubModel>> = dataHubDAO.getAllModels()

    suspend fun getModelByName(modelName: String): DataHubModel? = dataHubDAO.getModelByName(modelName)

    fun deleteModel(modelName: String, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val model = dataHubDAO.getModelByName(modelName)
                if (model != null) {
                    val datasetDir = File(model.modelPath)
                    if (datasetDir.exists()) {
                        datasetDir.deleteRecursively()
                    }
                    dataHubDAO.deleteModel(model)
                    withContext(Dispatchers.Main) {
                        onResult(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                println("Failed to delete model: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }

    fun updateModelLoadedStatus(modelName: String, isLoaded: Boolean, onResult: (Boolean) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val model = dataHubDAO.getModelByName(modelName)
                if (model != null) {
                    dataHubDAO.updateModel(model.copy(isLoaded = isLoaded))
                    withContext(Dispatchers.Main) {
                        onResult(true)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                println("Failed to update model status: ${e.message}")
                withContext(Dispatchers.Main) {
                    onResult(false)
                }
            }
        }
    }
}

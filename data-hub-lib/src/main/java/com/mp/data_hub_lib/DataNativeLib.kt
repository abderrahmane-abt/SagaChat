package com.mp.data_hub_lib

import android.content.Context
import com.mp.data_hub_lib.model.DataSetManifest
import kotlinx.serialization.json.Json
import net.lingala.zip4j.ZipFile
import java.io.File

class DataNativeLib {
    // Native methods
    external fun loadVecx(path: String, key: String): Boolean
    external fun updateEntity(entity: String, key: String, value: String): Boolean
    external fun getEntity(entity: String): String
    external fun saveVecx(path: String): Boolean

    companion object {
        init {
            try {
                System.loadLibrary("data_hub_lib")
                println("Native library loaded successfully.")
            } catch (e: Exception) {
                println("Failed to load native library: ${e.message}")
            }
        }
    }

    /**
     * Loads a data pack from a ZIP file and extracts it to the app's cache directory.
     */
    fun loadPack(packZip: String, password: String, context: Context): Pair<String, String>? {
        return try {
            val zipFile = ZipFile(packZip)
            if (!zipFile.isValidZipFile) {
                println("Invalid ZIP file.")
                return null
            }

            if (zipFile.isEncrypted) {
                zipFile.setPassword(password.toCharArray())
            }

            // Extract to cache directory
            zipFile.extractAll(context.cacheDir.absolutePath)

            val extractDir = context.cacheDir
            val vecxFile = extractDir.listFiles()?.find { it.name == "embeddings.vecx" }?.absolutePath
            val manifestFile = extractDir.listFiles()?.find { it.name == "manifest.json" }?.absolutePath
            println("ZIP file path: $packZip")
            println("Cache dir: ${context.cacheDir.absolutePath}")
            println("Extracted vecx file: $vecxFile")
            println("Extracted manifest file: $manifestFile")

            if (vecxFile != null && manifestFile != null) {
                // Load the extracted .vecx file
                val metadata = loadManifest(manifestFile)

                val loadSuccess = loadVecx(vecxFile, metadata?.vecxPasswordHint ?: "")
                if (loadSuccess) {
                    Pair(vecxFile, manifestFile)
                } else {
                    println("Failed to load .vecx file. Check file path and password.")
                    null
                }
            } else {
                println("Required files not found in ZIP.")
                null
            }
        } catch (e: Exception) {
            println("Failed to load pack: ${e.message}")
            null
        }
    }

    /**
     * Loads and parses manifest.json into a DataSetManifest object.
     */
    fun loadManifest(manifestPath: String): DataSetManifest? {
        return try {
            val jsonString = File(manifestPath).readText()
            Json.decodeFromString<DataSetManifest>(jsonString)
        } catch (e: Exception) {
            println("Failed to parse manifest: ${e.message}")
            null
        }
    }
}

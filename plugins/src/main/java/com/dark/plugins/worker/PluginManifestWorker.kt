package com.dark.plugins.worker

import android.util.Log
import com.dark.plugins.model.PluginManifest
import org.json.JSONObject

/**
 * PluginManifestWorker is responsible for reading plugin metadata
 * from a JSON-formatted manifest. The manifest can be provided either
 * as a raw JSON string or a JSONObject.
 *
 * This class ensures that the provided manifest is not null or empty
 * before parsing.
 */
class PluginManifestWorker {

    // Stores the plugin manifest JSON as a String (never null or empty)
    private val jsonCode: String

    /**
     * Constructor that accepts a raw JSON string.
     * Throws IllegalArgumentException if the input is null or blank.
     *
     * @param manifestCode JSON-formatted plugin manifest as a String
     */
    constructor(manifestCode: String) {
        require(manifestCode.isNotBlank()) { "Manifest JSON string cannot be null or empty" }
        this.jsonCode = manifestCode
    }

    /**
     * Constructor that accepts a JSONObject.
     * Throws IllegalArgumentException if the input is null.
     * Converts the JSONObject to a String for internal storage.
     *
     * @param manifestCode JSON-formatted plugin manifest as a JSONObject
     */
    constructor(manifestCode: JSONObject) {
        requireNotNull(manifestCode) { "Manifest JSONObject cannot be null" }
        this.jsonCode = manifestCode.toString()
    }

    /**
     * Retrieves the value of the "mainClass" property from the manifest.
     * This is usually the fully qualified name of the plugin's main class.
     *
     * @return The main class name as a String.
     * @throws org.json.JSONException if "mainClass" is missing in the manifest.
     */
    fun getMainClass(): String {
        return JSONObject(this.jsonCode).getString("mainClass")
    }

    /**
     * Retrieves the value of the "name" property from the manifest.
     * This is the human-readable name of the plugin.
     *
     * @return The plugin name as a String.
     * @throws org.json.JSONException if "name" is missing in the manifest.
     */
    fun getPluginName(): String {
        return JSONObject(this.jsonCode).getString("name")
    }

    /**
     * Retrieves the value of the "description" property from the manifest.
     * This describes the plugin's purpose or functionality.
     *
     * @return The plugin description as a String.
     * @throws org.json.JSONException if "description" is missing in the manifest.
     */
    fun getPluginDescription(): String {
        return JSONObject(this.jsonCode).getString("description")
    }

    /**
     * Retrieves the value of the "version" property from the manifest.
     * This indicates the plugin's version string, typically following
     * semantic versioning (e.g., "1.0.0").
     *
     * @return The plugin version as a String.
     * @throws org.json.JSONException if "version" is missing in the manifest.
     */
    fun getPluginVersion(): String {
        return JSONObject(this.jsonCode).getString("version")
    }

    /**
     * Creates a PluginManifest object from the current JSON manifest data.
     * This method extracts "name", "description", and "mainClass" values.
     *
     * @return A fully populated PluginManifest object.
     */
    fun getPluginManifest(): PluginManifest {
        return PluginManifest(
            name = getPluginName(),
            description = getPluginDescription(),
            mainClass = getMainClass(),
            rawCode = this.jsonCode
        )
    }

    /**
     * Returns the raw JSON manifest code stored in this worker.
     *
     * @return The manifest JSON as a String.
     */
    fun getManifestCode(): String {
        return this.jsonCode
    }

}

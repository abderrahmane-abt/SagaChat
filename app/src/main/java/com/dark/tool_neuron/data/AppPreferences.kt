package com.dark.tool_neuron.data

import android.content.Context
import android.util.Log
import com.dark.hxs.HexStorage
import com.dark.hxs.HxsRecord
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppPreferences @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val storage = HexStorage()
    private val basePath: String

    init {
        val dir = context.filesDir.resolve("app_prefs")
        dir.mkdirs()
        basePath = dir.absolutePath

        val exists = storage.exists(basePath)
        Log.d(TAG, "HXS vault exists=$exists at $basePath, dirExists=${dir.exists()}")
        if (exists) {
            storage.openPlaintext(basePath)
        } else {
            val created = storage.createPlaintext(basePath)
            Log.d(TAG, "createPlaintext result=$created")
        }
        val ensured = storage.ensureCollection(COLLECTION)
        Log.d(TAG, "ensureCollection=$ensured")
        storage.addIndex(COLLECTION, TAG_KEY, HexStorage.WIRE_BYTES)
        Log.d(TAG, "Collections: ${storage.listCollections()}, records: ${storage.count(COLLECTION)}")
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean {
        return findRecord(key)?.getBool(TAG_VALUE_BOOL, default) ?: default
    }

    fun putBoolean(key: String, value: Boolean) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putBool(TAG_VALUE_BOOL, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBool(TAG_VALUE_BOOL, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
        val rb = getBoolean(key)
        Log.d(TAG, "putBoolean($key, $value) -> readback=$rb, count=${storage.count(COLLECTION)}")
    }

    fun getString(key: String, default: String = ""): String {
        return findRecord(key)?.getString(TAG_VALUE_STRING, default) ?: default
    }

    fun putString(key: String, value: String) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putString(TAG_VALUE_STRING, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putString(TAG_VALUE_STRING, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    fun getBytes(key: String): ByteArray? {
        return findRecord(key)?.getBytes(TAG_VALUE_BYTES)
    }

    fun putBytes(key: String, value: ByteArray) {
        val existing = findRecord(key)
        if (existing != null) {
            existing.putBytes(TAG_VALUE_BYTES, value)
            storage.update(COLLECTION, existing)
        } else {
            val record = HxsRecord.build {
                putString(TAG_KEY, key)
                putBytes(TAG_VALUE_BYTES, value)
            }
            storage.put(COLLECTION, record)
        }
        storage.flushAll()
    }

    private fun findRecord(key: String): HxsRecord? {
        return storage.queryString(COLLECTION, TAG_KEY, key).firstOrNull()
    }

    // Convenience accessors for well-known keys
    var onboardingComplete: Boolean
        get() = getBoolean(KEY_ONBOARDING_COMPLETE)
        set(value) = putBoolean(KEY_ONBOARDING_COMPLETE, value)

    var tcAccepted: Boolean
        get() = getBoolean(KEY_TC_ACCEPTED)
        set(value) = putBoolean(KEY_TC_ACCEPTED, value)

    var setupDone: Boolean
        get() = getBoolean(KEY_SETUP_DONE)
        set(value) = putBoolean(KEY_SETUP_DONE, value)

    var securityMode: String
        get() = getString(KEY_SECURITY_MODE, SECURITY_NONE)
        set(value) = putString(KEY_SECURITY_MODE, value)

    var passwordSalt: ByteArray?
        get() = getBytes(KEY_PASSWORD_SALT)
        set(value) { if (value != null) putBytes(KEY_PASSWORD_SALT, value) }

    var passwordHash: ByteArray?
        get() = getBytes(KEY_PASSWORD_HASH)
        set(value) { if (value != null) putBytes(KEY_PASSWORD_HASH, value) }

    var securitySetupDone: Boolean
        get() = getBoolean(KEY_SECURITY_SETUP_DONE)
        set(value) = putBoolean(KEY_SECURITY_SETUP_DONE, value)

    var modelSetupDone: Boolean
        get() = getBoolean(KEY_MODEL_SETUP_DONE)
        set(value) = putBoolean(KEY_MODEL_SETUP_DONE, value)

    var guideShown: Boolean
        get() = getBoolean(KEY_GUIDE_SHOWN)
        set(value) = putBoolean(KEY_GUIDE_SHOWN, value)

    companion object {
        private const val COLLECTION = "app_prefs"
        private const val TAG_KEY = 1
        private const val TAG_VALUE_BOOL = 2
        private const val TAG_VALUE_STRING = 3
        private const val TAG_VALUE_BYTES = 4

        const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
        const val KEY_TC_ACCEPTED = "tc_accepted"
        const val KEY_SETUP_DONE = "setup_done"
        const val KEY_SECURITY_MODE = "security_mode"
        const val KEY_PASSWORD_SALT = "password_salt"
        const val KEY_PASSWORD_HASH = "password_hash"
        const val KEY_SECURITY_SETUP_DONE = "security_setup_done"
        const val KEY_MODEL_SETUP_DONE = "model_setup_done"
        const val KEY_GUIDE_SHOWN = "guide_shown"

        private const val TAG = "AppPreferences"

        const val SECURITY_NONE = "none"
        const val SECURITY_APP_PASSWORD = "app_password"
    }
}

package com.dark.tool_neuron.data

import android.util.Log
import com.dark.hxs_encryptor.HxsEncryptor
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityManager @Inject constructor(
    private val prefs: AppPreferences
) {
    private val encryptor = HxsEncryptor()

    val isLockEnabled: Boolean
        get() = prefs.securityMode == AppPreferences.SECURITY_APP_PASSWORD

    val isAppPassword: Boolean
        get() = prefs.securityMode == AppPreferences.SECURITY_APP_PASSWORD

    fun setPassword(password: String) {
        val salt = encryptor.randomBytes(16)
        val hash = deriveKey(password.toByteArray(), salt)
        prefs.passwordSalt = salt
        prefs.passwordHash = hash
        prefs.securityMode = AppPreferences.SECURITY_APP_PASSWORD
    }

    fun verifyPassword(password: String): Boolean {
        val salt = prefs.passwordSalt ?: return false
        val storedHash = prefs.passwordHash ?: return false
        val hash = deriveKey(password.toByteArray(), salt)
        return hash.contentEquals(storedHash)
    }

    private fun deriveKey(password: ByteArray, salt: ByteArray): ByteArray {
        return encryptor.argon2id(
            password = password,
            salt = salt,
            tCost = 2,
            mCost = 16384,
            parallelism = 2,
            hashLen = 32
        )
    }

    fun disableLock() {
        prefs.securityMode = AppPreferences.SECURITY_NONE
    }

    fun runAntiTamperChecks(): Boolean {
        if (encryptor.isDebuggerAttached()) return false
        if (encryptor.isFridaPresent()) return false
        if (encryptor.isXposedPresent()) return false
        return true
    }

    companion object {
        private const val TAG = "SecurityManager"
    }
}

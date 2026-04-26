package com.dark.tool_neuron.data

import java.security.MessageDigest

internal fun keyFingerprint(bytes: ByteArray): String {
    val h = MessageDigest.getInstance("SHA-256").digest(bytes)
    return h.take(4).joinToString("") { "%02x".format(it) }
}

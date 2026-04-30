package com.dark.tool_neuron.repo.research

import com.dark.gguf_lib.GGUFNativeLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object DocumentExtractor {
    suspend fun extract(
        bytes: ByteArray,
        mimeHint: String? = null,
        nameHint: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        if (bytes.isEmpty()) return@withContext null
        runCatching { GGUFNativeLib.nativeRagExtractText(bytes, mimeHint, nameHint) }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
    }
}

package com.dark.tool_neuron.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelStoreRepositoryTest {

    @Test
    fun supportedGgufFileAcceptsUppercaseExtension() {
        assertTrue(ModelStoreRepository.isSupportedGgufFile("models/Whisper-EN-Small.Q5_K_M.GGUF"))
    }

    @Test
    fun supportedGgufFileRejectsProjectionArtifacts() {
        assertFalse(ModelStoreRepository.isSupportedGgufFile("models/whisper-mmproj.Q4_K_M.GGUF"))
        assertFalse(ModelStoreRepository.isSupportedGgufFile("models/whisper-vision-adapter.gguf"))
        assertFalse(ModelStoreRepository.isSupportedGgufFile("models/whisper-projector.gguf"))
    }

    @Test
    fun stripGgufSuffixRemovesExtensionCaseInsensitively() {
        assertEquals(
            "Whisper-EN-Small.Q5_K_M",
            ModelStoreRepository.stripGgufSuffix("Whisper-EN-Small.Q5_K_M.GGUF")
        )
    }
}

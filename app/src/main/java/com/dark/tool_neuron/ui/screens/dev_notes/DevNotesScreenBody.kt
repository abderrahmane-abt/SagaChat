package com.dark.tool_neuron.ui.screens.dev_notes

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.dark.tool_neuron.ui.components.markdown.LocalMarkdownColors
import com.dark.tool_neuron.ui.components.markdown.lazyMarkdownItems
import com.dark.tool_neuron.ui.components.markdown.rememberMarkdownColors
import com.dark.tool_neuron.ui.theme.LocalDimens

private val DEV_NOTES = """
# Developer Notes

> Read this before you tap anything.

---

## Your previous data is gone

If you've used an earlier build, yes, it's wiped. On purpose.

We replaced the whole storage and encryption stack with **HXS** (Hex Storage). The old system worked fine, but I didn't want to keep building on it. HXS is written from scratch, no compatibility layer, no migration path from the old format.

- Encryption is designed around post-quantum algorithms
- Every data block is sealed and verified on read. Tampered files fail loudly
- No legacy shims carrying dead weight

If you lost something, I'm sorry. It won't happen again.

---

## What's new

### Core infrastructure
- **HXS** native hex-storage engine, C++ core with a Kotlin API
- **HXS Encryptor** symmetric + asymmetric encryption on top of HXS
- **Download Manager** multi-threaded native downloader for model files
- **GGUF Engine** on-device LLM inference via `custom-llama.cpp`, ARM64
- **AI Sherpa** on-device speech recognition via `custom-sherpa-onnx`

### UI and performance
- Markdown renderer rebuilt for streaming LLM output
  - Single-pass inline formatter, no intermediate `AnnotatedString` allocations
  - LRU parse cache, O(1) eviction
  - `CompositionLocal` for theme colors, no per-item recomposition
  - Math renderer skips 600+ symbol replaces when there's no backslash in the input
- Adaptive layouts across phones, tablets, foldables
- Material 3 Expressive, continuous-corner shapes, not the standard rectangles

---

## Known issues

- Model loading isn't wired to the UI yet, home screen is a placeholder
- Sherpa VAD works but there's no way to trigger it from the UI yet
- Image generation is out of scope for this build
- Download progress shown via foreground notification but no in-app download screen yet

---

*More soon.*
""".trimIndent()

@Composable
fun DevNotesScreen(innerPadding: PaddingValues) {
    val dimens = LocalDimens.current
    val markdownColors = rememberMarkdownColors()

    CompositionLocalProvider(LocalMarkdownColors provides markdownColors) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(
                horizontal = dimens.spacingMd,
                vertical = dimens.spacingMd
            )
        ) {
            lazyMarkdownItems(text = DEV_NOTES, keyPrefix = "dev_notes")
        }
    }
}

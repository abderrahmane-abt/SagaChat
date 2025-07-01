package com.dark.neuroverse.data.repo

import com.dark.neuroverse.data.model.ModelsData

object ModelsList {
    val jan_nano_guff = ModelsData(
        "Jan Nano Guff",
        "Jan Nano is a fine-tuned language model built on top of the Qwen3 architecture. it balances compact size and extended context length, making it ideal for efficient, high-quality text generation in local or embedded environments.",
        "https://huggingface.co/Menlo/Jan-nano-gguf/resolve/main/jan-nano-4b-Q4_K_M.gguf?download=true",
        "https://huggingface.co/Menlo/Jan-nano-gguf"
    )

    val modelList = listOf(jan_nano_guff)
}
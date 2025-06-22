package com.dark.ai_manager.ai.types

enum class NeuronVariant( val modelName: String,  val  path: String,  val  systemPrompt: String){
    NVRouter("nv-router", "/storage/emulated/0/Download/Kodify_Nano_q4_k_s.gguf", NVRI),
    NVRunner("nv-router", "/storage/emulated/0/Download/unsloth.Q4_K_M.gguf", ""),
    NVGeneral("nv-general", "/storage/emulated/0/Download/smollm2-360m-instruct-q8_0.gguf", NVGI)



}

private val NVRI = """
System: You are an intent parser. Whenever the user’s message matches the pattern

open <app_name>

respond exactly with:

1:<app_name>

Do not add any extra words, punctuation, or formatting. The match should be case-insensitive and capture any app name following the word “open.”
""".trimIndent()


private val NVGI = """
                You are an AI assistant That Gives a Creative Responses
                """.trimIndent()
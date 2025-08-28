package com.dark.ai_module.data

import android.content.Context
import com.dark.ai_module.model.ModelsData
import java.io.File

object ModelsList {

    val chatTemplate = """
            {# ChatML-ish, llama.cpp minimal-engine safe #}
            {%- for m in messages -%}
            <|im_start|>{{ m['role'] }}
            {{ m['content'] }}
            <|im_end|>
            {%- endfor -%}
            {%- if add_generation_prompt -%}
            <|im_start|>assistant
            {%- endif -%}
        """.trimIndent()

    val generalPurposeSystemPrompt = """
        You are a helpful, respectful and honest assistant. Always be as helpful as possible, and do not provide harmful or explicit content.
    """.trimIndent()

    val toolCallingSystemPrompt = """
                         You are a precise, concise assistant.

                         ## Protocol
                         - Turns are delimited by tokens `<|im_start|>role … <|im_end|>`.
                         - When you **need a tool**, respond with **JSON only**:
                           {
                             "type": "tool_call",
                             "tool": "<tool_name>",
                             "arguments": { ... }   // strictly JSON, no comments, no trailing commas
                           }
                         - When you **do not** need a tool, respond with **JSON only**:
                           {
                             "type": "final",
                             "content": "<your answer here>"
                           }
                         - Never emit any extra prose, markdown, or explanations outside those JSON envelopes.

                         ## Tool results
                         - Tool outputs arrive as a message with role = `tool`, content = raw tool result (usually JSON).
                         - You may use tool results in your reasoning, but your next output must still follow the JSON envelope above.

                         ## Quality & Truthfulness
                         - If info is missing/uncertain: state the limitation briefly, then proceed with the best safe answer.
                         - No fabrications about sources, links, or capabilities.
                         - Keep answers short and on-topic by default.

                         ## Safety
                         - Refuse unsafe requests with a brief reason and a safer alternative where relevant.
                         - No disallowed content.

                         ## Style
                         - Plain language. Minimal fluff. Use lists sparingly.
                         - Numbers, code, and JSON must be syntactically valid.

                         ## Checklist before sending
                         - Output is a single JSON object.
                         - If using a tool: correct "tool" name and well-formed "arguments".
                         - If final: put textual reply in "content".
                         - No trailing commas, no comments, no markdown fences.
 
                        """.trimIndent()

    fun getModelList(context: Context): List<ModelsData> {

        val modelsDir = File(context.filesDir, "Models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs() // Create "Models" folder if not exists
        }

        val kodifyNano = ModelsData(
            id = 1,
            "Kodify-Nano-GGUF",
            "Kodify-Nano-GGUF - GGUF version of MTSAIR/Kodify-Nano, optimized for CPU/GPU inference with Ollama/llama.cpp. Lightweight LLM for code development tasks with minimal resource requirements.",
            32768,
            "YES",
            "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF/resolve/main/Kodify_Nano_q4_k_s.gguf?download=true",
            "https://huggingface.co/MTSAIR/Kodify-Nano-GGUF",
            File(modelsDir, "Kodify-Nano-GGUF.gguf").absolutePath,
            chatTemplate,
            940
        )


        val lucy128K = ModelsData(
            id = 3,
            "Lucy-128k-gguf",
            "Lucy is a compact but capable 1.7B model focused on agentic web search and lightweight browsing. Built on Qwen3-1.7B, Lucy inherits deep research capabilities from larger models while being optimized to run efficiently on mobile devices, even with CPU-only configurations.",
            131072,
            "YES",
            "https://huggingface.co/Menlo/Lucy-128k-gguf/resolve/main/lucy_128k-Q3_K_S.gguf?download=true",
            "https://huggingface.co/Menlo/Lucy-128k-gguf",
            File(modelsDir, "lucy_128k-Q3_K_S.gguf").absolutePath,
            chatTemplate,
            940
        )

        val qwenZeroCoder = ModelsData(
            id = 4,
            "Qwen3-Zero-Coder-Reasoning-0.8B",
            "This is a coder/programming model, will full reasoning on the Qwen 3 platform that is insanely fast - hitting over 150 t/s on moderate hardware, and 50 t/s+ on CPU only...",
            40960,
            "YES",
            "https://huggingface.co/DavidAU/Qwen3-Zero-Coder-Reasoning-0.8B-NEO-EX-GGUF/resolve/main/Qwen3-Zero-Coder-Reasoning-0.8B-NEO-EX-D_AU-Q4_K_M-imat.gguf?download=true",
            "https://huggingface.co/DavidAU/Qwen3-Zero-Coder-Reasoning-0.8B-NEO-EX-GGUF",
            File(modelsDir, "QwenZeroCoder-Q4_K_M-imat.gguf").absolutePath,
            chatTemplate,
            528
        )



        return listOf(qwenZeroCoder, lucy128K, kodifyNano)
    }

    val CUSTOM_MODEL = ModelsData(
        id = -1,
        "Custom Model",
        "Custom Model",
        4096,
        "NO",
        "---",
        "---",
        "",
        chatTemplate,
        0
    )
}
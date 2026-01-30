package com.mp.n_apps.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * OpenAI-compatible Chat Completions client.
 * Works with OpenAI, Groq, OpenRouter, Together, HuggingFace, etc.
 * Supports native function/tool calling.
 * Auto-retries on HTTP 429 (rate limit) with backoff.
 */
object LLMClient {

    private const val TAG = "LLMClient"
    private const val MAX_RETRIES = 3

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val RETRY_DELAY_REGEX = Regex("try again in (\\d+\\.?\\d*)s")

    // ── Response types ──

    data class CompletionResult(
        val content: String?,
        val toolCalls: List<ToolCallItem>,
        val finishReason: String?,
        val usage: TokenUsage?
    )

    data class ToolCallItem(
        val id: String,
        val name: String,
        val arguments: String
    )

    data class TokenUsage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )

    // ── API call with retry ──

    suspend fun chatCompletion(
        apiKey: String,
        baseUrl: String,
        model: String,
        messages: JSONArray,
        tools: JSONArray?,
        maxTokens: Int = 4096,
        temperature: Float = 0.7f,
        toolChoice: String = "auto"
    ): Result<CompletionResult> = withContext(Dispatchers.IO) {
        val url = baseUrl.trimEnd('/') + "/v1/chat/completions"

        val requestBodyStr = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
                put("tool_choice", toolChoice)
            }
            put("max_tokens", maxTokens)
            put("temperature", temperature.toDouble())
        }.toString()

        var lastError: Exception = IOException("Request failed")

        for (attempt in 0..MAX_RETRIES) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $apiKey")
                    .header("Content-Type", "application/json")
                    .post(requestBodyStr.toRequestBody(JSON_MEDIA_TYPE))
                    .build()

                client.newCall(request).execute().use { response ->
                    val body = response.body.string()

                    if (response.code == 429 && attempt < MAX_RETRIES) {
                        val delaySec = parseRetryDelay(body) ?: (4.0 * (attempt + 1))
                        val delayMs = (delaySec * 1000).toLong().coerceIn(1000, 30000)
                        Log.d(TAG, "Rate limited (429), retrying in ${delaySec}s (attempt ${attempt + 1}/$MAX_RETRIES)")
                        Thread.sleep(delayMs)
                        lastError = IOException("Rate limited")
                        return@use // continue loop
                    }

                    if (!response.isSuccessful) {
                        val errorMsg = try {
                            val errorJson = JSONObject(body)
                            val errorObj = errorJson.optJSONObject("error")
                            errorObj?.optString("message", body.take(500))
                                ?: errorJson.optString("error", body.take(500))
                        } catch (_: Exception) {
                            body.take(500)
                        }
                        return@withContext Result.failure(
                            IOException("HTTP ${response.code}: $errorMsg")
                        )
                    }

                    return@withContext parseResponse(body)
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt < MAX_RETRIES) {
                    Log.d(TAG, "IO error, retrying: ${e.message}")
                    Thread.sleep(2000L * (attempt + 1))
                }
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
        }

        Result.failure(lastError)
    }

    private fun parseResponse(body: String): Result<CompletionResult> {
        return try {
            val json = JSONObject(body)
            val choices = json.optJSONArray("choices")
            if (choices == null || choices.length() == 0) {
                return Result.failure(IOException("No choices in response"))
            }

            val choice = choices.getJSONObject(0)
            val message = choice.getJSONObject("message")
            val content = if (message.has("content") && !message.isNull("content")) {
                message.getString("content")
            } else null
            val finishReason = if (choice.has("finish_reason") && !choice.isNull("finish_reason")) {
                choice.getString("finish_reason")
            } else null

            val toolCalls = mutableListOf<ToolCallItem>()
            val toolCallsArray = message.optJSONArray("tool_calls")
            if (toolCallsArray != null) {
                for (i in 0 until toolCallsArray.length()) {
                    val tc = toolCallsArray.getJSONObject(i)
                    val func = tc.getJSONObject("function")
                    toolCalls.add(ToolCallItem(
                        id = tc.getString("id"),
                        name = func.getString("name"),
                        arguments = func.getString("arguments")
                    ))
                }
            }

            val usage = json.optJSONObject("usage")?.let { usageObj ->
                TokenUsage(
                    promptTokens = usageObj.optInt("prompt_tokens", 0),
                    completionTokens = usageObj.optInt("completion_tokens", 0),
                    totalTokens = usageObj.optInt("total_tokens", 0)
                )
            }

            Result.success(CompletionResult(
                content = content,
                toolCalls = toolCalls,
                finishReason = finishReason,
                usage = usage
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse "try again in 3.8925s" from error message.
     */
    private fun parseRetryDelay(body: String): Double? {
        return try {
            val errorMsg = JSONObject(body).optJSONObject("error")?.optString("message", "") ?: ""
            RETRY_DELAY_REGEX.find(errorMsg)?.groupValues?.get(1)?.toDoubleOrNull()
        } catch (_: Exception) {
            null
        }
    }
}

package com.mp.n_apps.network

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

object SarvamApiClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://api.sarvam.ai/v1/chat/completions"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    data class ChatMessage(val role: String, val content: String)

    data class ChatCompletionResponse(
        val content: String,
        val finishReason: String?,
        val usage: TokenUsage?
    )

    data class TokenUsage(
        val promptTokens: Int,
        val completionTokens: Int,
        val totalTokens: Int
    )

    suspend fun chatCompletion(
        apiKey: String,
        messages: List<ChatMessage>,
        model: String = "sarvam-m",
        maxTokens: Int = 4096,
        temperature: Float = 0.7f
    ): Result<ChatCompletionResponse> = withContext(Dispatchers.IO) {
        try {
            val messagesArray = JSONArray().apply {
                messages.forEach { msg ->
                    put(JSONObject().apply {
                        put("role", msg.role)
                        put("content", msg.content)
                    })
                }
            }

            val requestBody = JSONObject().apply {
                put("model", model)
                put("messages", messagesArray)
                put("max_tokens", maxTokens)
                put("temperature", temperature.toDouble())
            }

            val request = Request.Builder()
                .url(BASE_URL)
                .header("api-subscription-key", apiKey)
                .header("Content-Type", "application/json")
                .post(requestBody.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                val body = response.body.string()

                if (!response.isSuccessful) {
                    val errorMsg = try {
                        val errorJson = JSONObject(body)
                        errorJson.optString("error",
                            errorJson.optJSONObject("error")?.optString("message", "") ?: "")
                    } catch (_: Exception) {
                        body.take(200)
                    }
                    return@withContext Result.failure(
                        IOException("HTTP ${response.code}: $errorMsg")
                    )
                }

                val json = JSONObject(body)
                val choices = json.optJSONArray("choices")
                if (choices == null || choices.length() == 0) {
                    return@withContext Result.failure(IOException("No choices in response"))
                }

                val choice = choices.getJSONObject(0)
                val message = choice.getJSONObject("message")
                val content = message.getString("content")
                val finishReason: String? =
                    if (choice.has("finish_reason")) choice.getString("finish_reason") else null

                val usage = json.optJSONObject("usage")?.let { usageObj ->
                    TokenUsage(
                        promptTokens = usageObj.optInt("prompt_tokens", 0),
                        completionTokens = usageObj.optInt("completion_tokens", 0),
                        totalTokens = usageObj.optInt("total_tokens", 0)
                    )
                }

                Result.success(ChatCompletionResponse(content, finishReason, usage))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

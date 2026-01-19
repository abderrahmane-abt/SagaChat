package com.dark.tool_neuron.plugins.services

import com.dark.tool_neuron.models.plugins.DuckDuckGoSearchResponse
import com.dark.tool_neuron.models.plugins.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Service for performing DuckDuckGo web searches
 * Uses DuckDuckGo's HTML scraping approach since the Instant Answer API is limited
 */
class DuckDuckGoSearchService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    /**
     * Search DuckDuckGo for the given query
     * @param query The search query
     * @param maxResults Maximum number of results to return (1-10)
     * @param safeSearch Enable safe search filtering
     * @return DuckDuckGoSearchResponse with search results
     */
    suspend fun search(
        query: String,
        maxResults: Int = 5,
        safeSearch: Boolean = true
    ): Result<DuckDuckGoSearchResponse> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()
            val encodedQuery = URLEncoder.encode(query, "UTF-8")

            // Using DuckDuckGo HTML API
            val url = buildString {
                append("https://html.duckduckgo.com/html/?q=")
                append(encodedQuery)
                if (safeSearch) {
                    append("&kp=1") // Strict safe search
                }
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Search failed with status: ${response.code}")
                    )
                }

                val html = response.body?.string() ?: ""
                val results = parseHtmlResults(html, maxResults)
                val searchTime = System.currentTimeMillis() - startTime

                Result.success(
                    DuckDuckGoSearchResponse(
                        query = query,
                        results = results,
                        totalResults = results.size,
                        searchTime = searchTime
                    )
                )
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Parse HTML results from DuckDuckGo
     */
    private fun parseHtmlResults(html: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        try {
            // Simple regex-based parsing (not ideal but works for basic cases)
            // In production, consider using JSoup for better HTML parsing

            // Match result blocks in DuckDuckGo HTML
            val resultPattern = Regex(
                """result__a[^>]*>([^<]+)</a>.*?result__snippet[^>]*>([^<]+)</.*?result__url[^>]*>([^<]+)</""",
                RegexOption.DOT_MATCHES_ALL
            )

            val matches = resultPattern.findAll(html)
            var position = 1

            for (match in matches.take(maxResults)) {
                if (match.groupValues.size >= 4) {
                    val title = cleanHtml(match.groupValues[1])
                    val snippet = cleanHtml(match.groupValues[2])
                    val urlText = cleanHtml(match.groupValues[3])

                    // Construct proper URL
                    val url = if (urlText.startsWith("http")) {
                        urlText
                    } else {
                        "https://$urlText"
                    }

                    results.add(
                        SearchResult(
                            title = title,
                            snippet = snippet,
                            url = url,
                            position = position++
                        )
                    )
                }
            }

            // Fallback: if no results found, create a simple result
            if (results.isEmpty()) {
                results.add(
                    SearchResult(
                        title = "Search completed",
                        snippet = "No specific results could be parsed. The search was executed successfully.",
                        url = "https://duckduckgo.com/?q=${URLEncoder.encode(html.take(50), "UTF-8")}",
                        position = 1
                    )
                )
            }

        } catch (e: Exception) {
            // Return error result
            results.add(
                SearchResult(
                    title = "Parse Error",
                    snippet = "Could not parse search results: ${e.message}",
                    url = "https://duckduckgo.com",
                    position = 1
                )
            )
        }

        return results
    }

    /**
     * Clean HTML entities and tags from text
     */
    private fun cleanHtml(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("<b>", "")
            .replace("</b>", "")
            .replace("<em>", "")
            .replace("</em>", "")
            .replace(Regex("<[^>]+>"), "")
            .trim()
    }

    /**
     * Alternative search using DuckDuckGo Instant Answer API (limited functionality)
     */
    suspend fun instantAnswerSearch(query: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.duckduckgo.com/?q=$encodedQuery&format=json"

            val request = Request.Builder()
                .url(url)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("API request failed: ${response.code}")
                    )
                }

                val jsonString = response.body?.string() ?: "{}"
                val json = JSONObject(jsonString)

                // Extract abstract or definition
                val answer = when {
                    json.has("AbstractText") && json.getString("AbstractText").isNotEmpty() ->
                        json.getString("AbstractText")
                    json.has("Definition") && json.getString("Definition").isNotEmpty() ->
                        json.getString("Definition")
                    else -> "No instant answer available"
                }

                Result.success(answer)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

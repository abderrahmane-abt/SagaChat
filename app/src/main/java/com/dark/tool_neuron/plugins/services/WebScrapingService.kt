package com.dark.tool_neuron.plugins.services

import com.dark.tool_neuron.models.plugins.ScrapedContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Service for web scraping using JSoup
 */
class WebScrapingService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /**
     * Scrape content from a URL
     * @param url The URL to scrape
     * @param selector Optional CSS selector to extract specific content
     * @param maxLength Maximum content length in characters
     * @return ScrapedContent with the extracted data
     */
    suspend fun scrape(
        url: String,
        selector: String? = null,
        maxLength: Int = 5000
    ): Result<ScrapedContent> = withContext(Dispatchers.IO) {
        try {
            val startTime = System.currentTimeMillis()

            // Validate URL
            if (!isValidUrl(url)) {
                return@withContext Result.failure(
                    IllegalArgumentException("Invalid URL format: $url")
                )
            }

            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to fetch URL: ${response.code} ${response.message}")
                    )
                }

                val html = response.body?.string() ?: ""
                val fetchTime = System.currentTimeMillis() - startTime

                // Parse HTML with JSoup
                val document: Document = Jsoup.parse(html, url)

                // Extract content
                val extractedContent = if (!selector.isNullOrBlank()) {
                    extractBySelector(document, selector)
                } else {
                    extractMainContent(document)
                }

                // Get metadata
                val metadata = extractMetadata(document, selector)

                // Create response
                val content = ScrapedContent(
                    url = url,
                    title = document.title() ?: "Untitled",
                    content = extractedContent.take(maxLength),
                    contentLength = extractedContent.length,
                    fetchTime = fetchTime,
                    metadata = metadata
                )

                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract content using CSS selector
     */
    private fun extractBySelector(document: Document, selector: String): String {
        return try {
            val elements = document.select(selector)
            if (elements.isEmpty()) {
                "No elements found matching selector: $selector"
            } else {
                elements.joinToString("\n\n") { element ->
                    element.text()
                }
            }
        } catch (e: Exception) {
            "Error extracting content with selector '$selector': ${e.message}"
        }
    }

    /**
     * Extract main content from the page (fallback when no selector is provided)
     */
    private fun extractMainContent(document: Document): String {
        // Remove script and style elements
        document.select("script, style, nav, header, footer, aside").remove()

        // Try to find main content area
        val mainContent = document.select("article, main, .content, #content").firstOrNull()
            ?: document.body()

        return buildString {
            // Get all paragraphs and headings
            val elements = mainContent.select("h1, h2, h3, h4, h5, h6, p, li")

            for (element in elements) {
                val text = element.text().trim()
                if (text.length > 20) { // Filter out very short snippets
                    appendLine(text)
                    appendLine()
                }
            }
        }.trim()
    }

    /**
     * Extract metadata from the document
     */
    private fun extractMetadata(document: Document, selector: String?): Map<String, String> {
        val metadata = mutableMapOf<String, String>()

        // Selector used
        metadata["selector"] = selector?.takeIf { it.isNotBlank() } ?: "none"

        // Meta description
        document.select("meta[name=description]").firstOrNull()?.attr("content")?.let {
            if (it.isNotBlank()) metadata["description"] = it
        }

        // Author
        document.select("meta[name=author]").firstOrNull()?.attr("content")?.let {
            if (it.isNotBlank()) metadata["author"] = it
        }

        // Keywords
        document.select("meta[name=keywords]").firstOrNull()?.attr("content")?.let {
            if (it.isNotBlank()) metadata["keywords"] = it
        }

        // Canonical URL
        document.select("link[rel=canonical]").firstOrNull()?.attr("href")?.let {
            if (it.isNotBlank()) metadata["canonical_url"] = it
        }

        // Language
        document.select("html").firstOrNull()?.attr("lang")?.let {
            if (it.isNotBlank()) metadata["language"] = it
        }

        // Total paragraph count
        metadata["paragraph_count"] = document.select("p").size.toString()

        // Total image count
        metadata["image_count"] = document.select("img").size.toString()

        // Total link count
        metadata["link_count"] = document.select("a").size.toString()

        return metadata
    }

    /**
     * Validate URL format
     */
    private fun isValidUrl(url: String): Boolean {
        return try {
            val lowercaseUrl = url.lowercase()
            lowercaseUrl.startsWith("http://") || lowercaseUrl.startsWith("https://")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Extract all links from a page
     */
    suspend fun extractLinks(url: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to fetch URL: ${response.code}")
                    )
                }

                val html = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(html, url)

                val links = document.select("a[href]")
                    .mapNotNull { it.absUrl("href") }
                    .filter { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
                    .distinct()

                Result.success(links)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Extract all images from a page
     */
    suspend fun extractImages(url: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Android; Mobile) AppleWebKit/537.36")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("Failed to fetch URL: ${response.code}")
                    )
                }

                val html = response.body?.string() ?: ""
                val document: Document = Jsoup.parse(html, url)

                val images = document.select("img[src]")
                    .mapNotNull { it.absUrl("src") }
                    .filter { it.isNotBlank() && (it.startsWith("http://") || it.startsWith("https://")) }
                    .distinct()

                Result.success(images)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

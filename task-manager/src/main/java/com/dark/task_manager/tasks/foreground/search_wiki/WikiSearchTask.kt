package com.dark.task_manager.tasks.foreground.search_wiki

import android.content.Context
import android.util.Log
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.concurrent.thread

class WikiSearchTask(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Wiki Search",
            description = "Search Wikipedia for quick summaries. Pass { query: String }.",
            args = """{ query: String }""".trimIndent(),
            taskType = TaskType.FOREGROUND
        )
    }

    override fun onStart(any: Any) {
        Log.d(getTaskInfo().taskName, "WikiSearchTask started")
    }

    override fun onRun(any: Any): Any {
        val args = any as? JSONObject ?: return JSONObject().put("error", "Invalid arguments")
        val query = args.optString("query", "").trim()

        if (query.isEmpty()) {
            Log.w(getTaskInfo().taskName, "No query provided")
            return JSONObject().put("error", "No query provided")
        }

        val summary = searchWiki(query)
        Log.d(getTaskInfo().taskName, "Result for '$query': $summary")

        return JSONObject().put("result", summary)
    }


    override fun onStop() {
        Log.d(getTaskInfo().taskName, "WikiSearchTask stopped")
    }

    private fun searchWiki(query: String): String {
        val encodedQuery = URLEncoder.encode(query, "UTF-8")
        val apiUrl = "https://en.wikipedia.org/api/rest_v1/page/summary/$encodedQuery"

        return try {
            val connection = URL(apiUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            when (connection.responseCode) {
                connection.responseCode -> {
                    val response = connection.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    json.optString("extract", "No summary available.")
                }
                connection.responseCode -> {
                    "No Wikipedia page found for '$query'. Try searching something more common."
                }
                else -> {
                    "Error: Wikipedia API returned ${connection.responseCode}"
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error fetching data: ${e.localizedMessage}"
        }
    }

}

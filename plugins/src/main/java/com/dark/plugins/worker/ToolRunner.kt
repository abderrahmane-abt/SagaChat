package com.dark.plugins.worker

import android.content.Context
import android.util.Log
import com.dark.plugins.model.LoadedPlugin
import org.json.JSONObject

object ToolRunner {

    fun run(loadedPlugin: LoadedPlugin, context: Context) {
        Log.d("ToolRunner", "Running tool for plugin ${loadedPlugin.manifest?.name}")
        val toolCall = JSONObject().apply {
            put("type", "tool_call")
            put("tool", "web_search")
            put("arguments", JSONObject().apply {
                put("query", "What is the capital of France?")
            })
        }

        if (loadedPlugin.api == null) Log.e("ToolRunner", "API is null")
        if (loadedPlugin.api != null) Log.e("ToolRunner", "API is Not Null ${loadedPlugin.api.getPluginInfo()}")

        loadedPlugin.api?.runTool(context, toolCall.getString("tool"), toolCall.getJSONObject("arguments")) { result ->
            Log.d("ToolRunner", "Tool result: $result")
        }
    }


}
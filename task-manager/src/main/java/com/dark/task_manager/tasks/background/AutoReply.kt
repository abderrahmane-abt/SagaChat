package com.dark.task_manager.tasks.background

import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dark.ai_manager.ai.local.Neuron
import com.dark.ai_manager.ai.local.NeuronVariant
import com.dark.task_manager.api.TaskApi
import com.dark.task_manager.model.TaskInfo
import com.dark.task_manager.model.TaskType
import com.dark.task_manager.services.NotificationListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
class AutoReply(context: Context) : TaskApi(context) {

    override fun getTaskInfo(): TaskInfo {
        return TaskInfo(
            taskName = "Auto Reply",
            description = "",
            systemPrompt = "",
            taskType = TaskType.BACKGROUND
        )
    }

    override fun onStart(any: Any) {
        if (isGenerating) return
        Neuron.unloadAllModels()
        Neuron.loadModel(
            NeuronVariant.NVRunner,
            systemPrompt = "You are an AI assistant that sends automatic replies when the user is unavailable. Respond briefly, politely, and relevant to the incoming message. Keep all replies under 1-2 short sentences. Never generate long paragraphs, filler, or unrelated text. Assume the user is busy, driving, or in a meeting.\n"
        )
    }


    override fun onRun(any: Any) {
        if (isGenerating) return
        onReceived(any as Pair<NotificationListener, StatusBarNotification>)
    }


    private var lastMessageId = ""
    private var isGenerating = false

    fun onReceived(pair: Pair<NotificationListener, StatusBarNotification>) {
        val listener = pair.first
        val sbn = pair.second

        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val message = extras.getString("android.text") ?: ""
        val key = sbn.key

        if (!packageName.contains("whatsapp", true)) return
        if (key == lastMessageId) return

        lastMessageId = key

        Log.d("NotificationListener", "🔔 Message from $title: $message")

        val actions = sbn.notification.actions ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue
            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey.lowercase().contains("reply")) {

                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            // Get AI-generated reply
                            isGenerating = true
                            val aiResponse = Neuron.generateResponseStreaming("Keep the Response Small here is the Message  $message") { }

                            val finalReply = aiResponse.ifBlank { "I'm busy, will reply later." }

                            val replyIntent = action.actionIntent
                            val replyBundle = Bundle().apply {
                                putCharSequence(remoteInput.resultKey, finalReply)
                            }
                            val intent = Intent()
                            RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, replyBundle)

                            replyIntent.send(listener, 0, intent)

                            Log.d("NotificationListener", "✅ Auto-reply sent: $finalReply")
                            listener.cancelNotification(sbn.key)
                            isGenerating = false
                        } catch (e: Exception) {
                            Log.e("NotificationListener", "❌ Failed to send AI reply", e)
                        }
                    }
                    return
                }
            }
        }
    }


}
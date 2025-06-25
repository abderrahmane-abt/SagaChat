package com.dark.task_manager.services

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.dark.task_manager.register.TaskRegistry

class NotificationListener : NotificationListenerService() {

    private var lastMessage = ""

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("NotificationListener", "✅ Notification Listener Connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        TaskRegistry.startTask("Auto Reply", Pair<NotificationListenerService, StatusBarNotification>(this, sbn))
        //onRecived(sbn)
    }

    fun onRecived(sbn: StatusBarNotification){
        val packageName = sbn.packageName
        val extras = sbn.notification.extras
        val title = extras.getString("android.title") ?: ""
        val message = extras.getString("android.text") ?: ""

        if (!packageName.contains("whatsapp", true)) return

        Log.d("NotificationListener", "🔔 Message from $title: $message")

        // Prevent replying to own messages or duplicates
        if (message == lastMessage || message.contains("I'm busy")) return

        lastMessage = message

        val actions = sbn.notification.actions ?: return

        for (action in actions) {
            val remoteInputs = action.remoteInputs ?: continue

            for (remoteInput in remoteInputs) {
                if (remoteInput.resultKey.lowercase().contains("reply")) {
                    val replyIntent = action.actionIntent

                    val replyBundle = Bundle().apply {
                        putCharSequence(remoteInput.resultKey, "I'm busy, will reply later.")
                    }

                    val intent = Intent()
                    RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, replyBundle)

                    try {
                        replyIntent.send(this, 0, intent)
                        Log.d("NotificationListener", "✅ Auto-reply sent to $title")
                    } catch (e: Exception) {
                        Log.e("NotificationListener", "❌ Failed to send reply", e)
                    }

                    cancelNotification(sbn.key)
                    return // Stop after first valid reply
                }
            }
        }

    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d("NotificationListener", "❌ Notification removed from ${sbn.packageName}")
    }
}

package com.ai.assistant.service

import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationReaderService : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification?.extras ?: return

        val title = extras.getString("android.title") ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""

        if ((packageName == "com.whatsapp" || packageName == "com.google.android.apps.messaging") 
            && text.isNotEmpty() && !title.lowercase().contains("whatsapp")) {

            val intent = Intent("com.ai.assistant.READ_NOTIFICATION").apply {
                putExtra("SENDER", title)
                putExtra("MESSAGE", text)
            }
            sendBroadcast(intent)
        }
    }
}

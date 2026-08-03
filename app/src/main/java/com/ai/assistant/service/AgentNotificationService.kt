package com.ai.assistant.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class AgentNotificationService : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val packageName = sbn?.packageName ?: return
        val extras = sbn.notification?.extras
        val title = extras?.getString("android.title")
        val text = extras?.getCharSequence("android.text")?.toString()

        // Yahan WhatsApp / SMS notification processing logic aayegi
    }
}

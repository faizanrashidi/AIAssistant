package com.ai.assistant.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.ai.assistant.AssistantApplication
import com.ai.assistant.R

class CoreForegroundService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification: Notification = NotificationCompat.Builder(this, AssistantApplication.CHANNEL_ID)
            .setContentTitle("AI System Agent Active")
            .setContentText("Monitoring system events & voice triggers...")
            .setSmallIcon(android.R.drawable.sym_def_app_icon)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
        
        // Background AI Tasks / Listeners yahan init karein
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

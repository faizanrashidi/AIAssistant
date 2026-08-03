package com.ai.assistant.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import java.util.Locale

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    private var tts: TextToSpeech? = null

    override fun onReceive(context: Context?, intent: Intent?) {
        if (intent == null || context == null) return

        val geofencingEvent = GeofencingEvent.fromIntent(intent) ?: return

        if (geofencingEvent.hasError()) {
            val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
            Toast.makeText(context, "Geofence Error: $errorMessage", Toast.LENGTH_SHORT).show()
            return
        }

        val geofenceTransition = geofencingEvent.geofenceTransition
        if (geofenceTransition == Geofence.GEOFENCE_TRANSITION_ENTER) {
            val reminderMessage = intent.getStringExtra("REMINDER_TEXT") ?: "Aapki location par reminder set tha."

            tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale("hi", "IN")
                    tts?.speak("Reminder Alert! $reminderMessage", TextToSpeech.QUEUE_FLUSH, null, "")
                }
            }

            Toast.makeText(context, "Location Alert: $reminderMessage", Toast.LENGTH_LONG).show()
        }
    }
}

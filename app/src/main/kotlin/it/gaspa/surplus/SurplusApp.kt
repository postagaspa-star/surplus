package it.gaspa.surplus

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager

class SurplusApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_REMINDERS,
            "Promemoria spuntini",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Promemoria per gli spuntini, agli orari che imposti tu"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
    }
}

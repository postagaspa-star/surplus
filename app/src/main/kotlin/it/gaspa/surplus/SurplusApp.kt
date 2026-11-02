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
        val aggiornamenti = NotificationChannel(
            CANALE_AGGIORNAMENTI,
            "Aggiornamenti dell'app",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Avvisa solo se Android chiede di confermare un aggiornamento"
        }
        getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(channel, aggiornamenti))
    }

    companion object {
        const val CHANNEL_REMINDERS = "reminders"
        const val CANALE_AGGIORNAMENTI = "aggiornamenti"
    }
}

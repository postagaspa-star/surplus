package it.gaspa.surplus.notify

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import it.gaspa.surplus.MainActivity
import it.gaspa.surplus.R
import it.gaspa.surplus.SurplusApp
import it.gaspa.surplus.data.ReminderKey
import it.gaspa.surplus.data.SettingsRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY)
            ?.let { runCatching { ReminderKey.valueOf(it) }.getOrNull() }
            ?: return
        showNotification(context, key)
        val settings = runBlocking { SettingsRepo(context).flow.first() }
        // In modalità dinamica il post-allenamento è one-shot: si riarma alla prossima serie.
        if (key == ReminderKey.POST_ALLENAMENTO && settings.postWorkoutDynamic) return
        // Altrimenti riprogramma la prossima occorrenza secondo le impostazioni correnti.
        settings.reminders.find { it.key == key }?.let { ReminderScheduler.schedule(context, it) }
    }

    private fun showNotification(context: Context, key: ReminderKey) {
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        val (title, text) = when (key) {
            ReminderKey.META_MATTINA ->
                "Spuntino di metà mattina" to "Un frutto + frutta secca (30 g), oppure yogurt e qualche tarallo."
            ReminderKey.POST_ALLENAMENTO ->
                "Spuntino dopo allenamento" to "Panino con affettato o formaggio, oppure yogurt greco + frutto + taralli."
            ReminderKey.PRE_NANNA ->
                "Spuntino prima di dormire" to "Yogurt greco, un bicchiere di latte o una manciata di frutta secca."
        }

        val contentIntent = PendingIntent.getActivity(
            context, 100,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = Notification.Builder(context, SurplusApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()

        context.getSystemService(NotificationManager::class.java)?.notify(key.idCode, notification)
    }

    companion object {
        const val EXTRA_KEY = "reminder"
    }
}

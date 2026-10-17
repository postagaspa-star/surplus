package it.gaspa.surplus.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import it.gaspa.surplus.data.SettingsRepo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/** Dopo un riavvio del telefono le sveglie vanno riprogrammate. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = runBlocking { SettingsRepo(context).flow.first() }
        ReminderScheduler.rescheduleAll(context, settings)
    }
}

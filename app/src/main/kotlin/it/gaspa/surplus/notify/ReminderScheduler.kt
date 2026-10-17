package it.gaspa.surplus.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import it.gaspa.surplus.data.ReminderCfg
import it.gaspa.surplus.data.ReminderKey
import it.gaspa.surplus.data.Settings
import it.gaspa.surplus.logic.NextOccurrence
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Programma le sveglie dei promemoria spuntini. Esatte dove possibile
 * (app sideload: USE_EXACT_ALARM), altrimenti finestra di 15 minuti.
 */
object ReminderScheduler {

    fun rescheduleAll(context: Context, settings: Settings) {
        settings.reminders.forEach { cfg ->
            if (cfg.key == ReminderKey.POST_ALLENAMENTO && settings.postWorkoutDynamic) {
                // Modalità dinamica: niente sveglia a orario fisso, si arma a ogni serie.
                context.getSystemService(AlarmManager::class.java)?.cancel(pendingIntent(context, cfg.key))
            } else {
                schedule(context, cfg)
            }
        }
    }

    /**
     * Arma (o fa scorrere) il promemoria post-allenamento a N minuti da adesso.
     * Stesso PendingIntent della sveglia fissa: ogni serie nuova sostituisce la precedente.
     */
    fun armPostWorkoutIn(context: Context, delayMin: Int) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, ReminderKey.POST_ALLENAMENTO)
        val triggerAt = System.currentTimeMillis() + delayMin * 60_000L
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 5 * 60_000L, pi)
        }
    }

    fun schedule(context: Context, cfg: ReminderCfg) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, cfg.key)
        am.cancel(pi)
        if (!cfg.enabled) return
        val next = NextOccurrence.compute(LocalDateTime.now(), cfg.hour, cfg.minute, cfg.daysMask) ?: return
        val triggerAt = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val canExact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        if (canExact) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        } else {
            am.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, 15 * 60_000L, pi)
        }
    }

    private fun pendingIntent(context: Context, key: ReminderKey): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            key.idCode,
            Intent(context, ReminderReceiver::class.java).putExtra(ReminderReceiver.EXTRA_KEY, key.name),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}

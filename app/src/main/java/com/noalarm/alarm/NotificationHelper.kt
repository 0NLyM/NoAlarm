package com.noalarm.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.noalarm.Format
import com.noalarm.MainActivity
import com.noalarm.R
import com.noalarm.data.Alarm
import com.noalarm.data.Store

object NotificationHelper {

    const val CH_ALARM = "alarm"
    const val CH_ALARM_WATCH = "alarm_watch"
    const val CH_UPCOMING = "upcoming"
    const val CH_CLOCK = "clock"
    const val CH_BEDTIME = "bedtime"

    const val ID_RINGING = 1001
    const val ID_UPCOMING = 1002
    const val ID_CLOCK = 1003
    const val ID_BEDTIME = 1004
    const val ID_SNOOZED = 1005
    const val ID_MISSED = 1006
    const val ID_STOPWATCH = 1007
    const val ID_RINGING_FGS = 1008

    fun createChannels(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_ALARM, c.getString(R.string.channel_alarm), NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)          // il suono lo gestisce AlarmService
                enableVibration(false)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            // Suono/vibrazione di default (non silenziati come CH_ALARM): il
            // watch sembra decidere popup/vibrazione dell'eco bridgeata in base
            // a queste impostazioni del canale, non solo da categoria/priorita'.
            NotificationChannel(CH_ALARM_WATCH, c.getString(R.string.channel_alarm_watch), NotificationManager.IMPORTANCE_HIGH).apply {
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_UPCOMING, c.getString(R.string.channel_upcoming), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_CLOCK, c.getString(R.string.channel_timer), NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BEDTIME, c.getString(R.string.channel_bedtime), NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun open(c: Context, tab: String? = null): PendingIntent = PendingIntent.getActivity(
        c, 0,
        Intent(c, MainActivity::class.java).apply { tab?.let { putExtra(MainActivity.EXTRA_TAB, it) } },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    fun action(c: Context, name: String, id: Long, extra: Int = 0): PendingIntent = PendingIntent.getBroadcast(
        c, (name + id + extra).hashCode(),
        Intent(c, ActionReceiver::class.java).setAction(name)
            .putExtra(AlarmScheduler.EXTRA_ID, id)
            .putExtra(ActionReceiver.EXTRA_VALUE, extra),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun fullScreenIntent(c: Context, alarm: Alarm): PendingIntent = PendingIntent.getActivity(
        c, alarm.id.hashCode(),
        Intent(c, AlarmActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(AlarmScheduler.EXTRA_ID, alarm.id),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /**
     * Notifica bridgeabile su Wear OS mostrata mentre la sveglia suona —
     * l'UNICA notifica pensata per essere vista/usata dall'utente, sia sul
     * telefono che sul watch. Canale [CH_ALARM_WATCH] (suono/vibrazione di
     * default, a differenza di [CH_ALARM]): confermato su TicWatch E3 con
     * popup, vibrazione e azioni Posticipa/Spegni funzionanti.
     *
     * Porta anche lo schermo intero sul telefono ([setFullScreenIntent]):
     * prima stava sulla notifica separata [foregroundPlaceholder], ma
     * spostarlo qui elimina il "doppione" che l'utente vedeva sul telefono
     * (due notifiche sveglia, una delle due senza pulsanti) — fullScreenIntent
     * non e' fra le 4 condizioni che escludono una notifica dal bridging
     * (Causa 12), quindi non rischia di rompere l'eco sul watch.
     */
    fun ringing(c: Context, alarm: Alarm): Notification {
        val full = fullScreenIntent(c, alarm)
        val s = Store.settings.value
        return NotificationCompat.Builder(c, CH_ALARM_WATCH)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(alarm.label.ifBlank { "Sveglia" })
            .setContentText(Format.hhmm(alarm.hour, alarm.minute, s.use24h))
            .setContentIntent(full)
            .setFullScreenIntent(full, true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_stat_alarm, "POSTICIPA", action(c, ActionReceiver.SNOOZE, alarm.id))
            .addAction(R.drawable.ic_stat_alarm, "SPEGNI", action(c, ActionReceiver.DISMISS, alarm.id))
            .setLocalOnly(!alarm.ringOnWatch)
            .build()
    }

    /**
     * Notifica-segnaposto: serve solo a soddisfare l'obbligo di Android di
     * mostrare una notifica per `startForeground(..., MEDIA_PLAYBACK)`, mai
     * pensata per l'utente. Deliberatamente minima (nessun titolo/orario
     * della sveglia, nessuna azione) cosi' non sembra una seconda sveglia con
     * i pulsanti mancanti — quella vera, con tutto il resto, e' [ringing].
     * Resta su [CH_ALARM] e sempre `setLocalOnly(true)`: e' comunque ongoing
     * per via del foreground service (Causa 12), quindi Wear OS la
     * scarterebbe da sola — locale esplicitamente solo per non offrirla
     * proprio come seconda copia inutile.
     */
    fun foregroundPlaceholder(c: Context, alarm: Alarm): Notification =
        NotificationCompat.Builder(c, CH_ALARM)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("NoAlarm")
            .setContentIntent(fullScreenIntent(c, alarm))
            .setLocalOnly(true)
            .build()

    /**
     * Preavviso di questa sveglia, programmato dal sistema esattamente
     * [Alarm.reminderMinutes] prima del suono: un solo colpo, non una
     * notifica che resta li' finche' non cambia qualcosa.
     */
    fun showAlarmReminder(c: Context, alarm: Alarm) {
        val s = Store.settings.value
        val n = NotificationCompat.Builder(c, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Sveglia alle ${Format.hhmm(alarm.hour, alarm.minute, s.use24h)}")
            .setContentText(
                "Fra ${alarm.reminderMinutes} min" +
                    (if (alarm.label.isBlank()) "" else " · ${alarm.label}")
            )
            .setContentIntent(open(c, MainActivity.TAB_ALARM))
            .setAutoCancel(true)
            .addAction(
                0,
                if (alarm.repeating) "IGNORA UNA VOLTA" else "ELIMINA",
                action(c, ActionReceiver.SKIP_NEXT, alarm.id),
            )
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(ID_UPCOMING + alarm.id.hashCode(), n) }
    }

    fun showSnoozed(c: Context, alarm: Alarm, until: Long) {
        val n = NotificationCompat.Builder(c, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Posticipata a ${Format.hhmm(hourOf(until), minuteOf(until), Store.settings.value.use24h)}")
            .setContentText(alarm.label.ifBlank { "Sveglia" } + " · " + Format.until(until))
            .setContentIntent(open(c, MainActivity.TAB_ALARM))
            .setOngoing(true)
            .addAction(0, "SPEGNI", action(c, ActionReceiver.DISMISS, alarm.id))
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(ID_SNOOZED + alarm.id.hashCode(), n) }
    }

    fun cancelSnoozed(c: Context, id: Long) =
        NotificationManagerCompat.from(c).cancel(ID_SNOOZED + id.hashCode())

    fun showMissed(c: Context, alarm: Alarm) {
        val n = NotificationCompat.Builder(c, CH_UPCOMING)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Sveglia non udita")
            .setContentText(
                Format.hhmm(alarm.hour, alarm.minute, Store.settings.value.use24h) +
                    (if (alarm.label.isBlank()) "" else " · ${alarm.label}")
            )
            .setContentIntent(open(c, MainActivity.TAB_ALARM))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(ID_MISSED + alarm.id.hashCode(), n) }
    }

    fun showBedtime(c: Context, minutes: Int) {
        val n = NotificationCompat.Builder(c, CH_BEDTIME)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle("Ora di andare a letto")
            .setContentText("Fra $minutes minuti, per svegliarti riposato.")
            .setContentIntent(open(c, MainActivity.TAB_ALARM))
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(c).notify(ID_BEDTIME, n) }
    }

    private fun hourOf(ms: Long) = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault()).hour

    private fun minuteOf(ms: Long) = java.time.Instant.ofEpochMilli(ms)
        .atZone(java.time.ZoneId.systemDefault()).minute
}

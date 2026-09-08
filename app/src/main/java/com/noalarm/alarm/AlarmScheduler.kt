package com.noalarm.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.noalarm.MainActivity
import com.noalarm.data.Alarm
import com.noalarm.data.Store
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object AlarmScheduler {

    const val EXTRA_ID = "alarm_id"
    const val ACTION_RING = "com.noalarm.RING"
    const val ACTION_REMIND = "com.noalarm.REMIND"
    const val ACTION_BEDTIME = "com.noalarm.BEDTIME"
    private const val BEDTIME_ID = -1L

    private fun manager(c: Context) = c.getSystemService(AlarmManager::class.java)

    private fun pending(c: Context, id: Long, action: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id)
        return PendingIntent.getBroadcast(
            c, id.hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun canScheduleExact(c: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || manager(c).canScheduleExactAlarms()

    /**
     * Spegne le sveglie che non suoneranno piu': una data singola ormai passata,
     * o una ripetizione le cui occorrenze sono state tutte saltate.
     */
    fun pruneExpired(c: Context) {
        Store.alarms.value
            .filter { it.enabled && it.snoozedUntil == 0L && it.nextTrigger() == null }
            .forEach { save(c, it.copy(enabled = false, skipNext = false)) }
    }

    /** Riallinea tutte le sveglie di sistema allo stato dello Store. */
    fun syncAll(c: Context) {
        pruneExpired(c)
        Store.alarms.value.forEach { schedule(c, it) }
        scheduleBedtime(c)
    }

    fun schedule(c: Context, alarm: Alarm) {
        val pi = pending(c, alarm.id, ACTION_RING)
        val at = alarm.nextTrigger()
        if (at == null) {
            manager(c).cancel(pi)
        } else {
            val show = PendingIntent.getActivity(
                c, alarm.id.hashCode(), Intent(c, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            // setAlarmClock: esatta, ignora Doze e compare nella status bar del sistema.
            manager(c).setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
        }
        scheduleReminder(c, alarm, at)
    }

    /**
     * Notifica di preavviso, quanti minuti prima decide la sveglia stessa
     * (0 = nessuna): a differenza della vecchia notifica sempre presente,
     * questa e' un singolo colpo programmato al suo istante esatto, non
     * qualcosa che resta in tendina finche' non cambia qualcosa.
     */
    private fun scheduleReminder(c: Context, alarm: Alarm, at: Long?) {
        val pi = pending(c, alarm.id, ACTION_REMIND)
        val remindAt = at?.minus(alarm.reminderMinutes * 60_000L)
        if (remindAt == null || alarm.reminderMinutes <= 0 || remindAt <= System.currentTimeMillis()) {
            manager(c).cancel(pi)
        } else {
            manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, remindAt, pi)
        }
    }

    fun cancel(c: Context, id: Long) {
        manager(c).cancel(pending(c, id, ACTION_RING))
        manager(c).cancel(pending(c, id, ACTION_REMIND))
    }

    /** Salva la sveglia e la riprogramma, promemoria compreso. */
    fun save(c: Context, alarm: Alarm) {
        Store.putAlarm(alarm)
        schedule(c, alarm)
    }

    fun delete(c: Context, id: Long) {
        cancel(c, id)
        Store.removeAlarm(id)
    }

    /** La prossima sveglia che suonera', con il suo istante. */
    fun next(): Pair<Alarm, Long>? = Store.alarms.value
        .mapNotNull { a -> a.nextTrigger()?.let { a to it } }
        .minByOrNull { it.second }

    fun scheduleBedtime(c: Context) {
        val s = Store.settings.value
        val pi = pending(c, BEDTIME_ID, ACTION_BEDTIME)
        if (!s.bedtimeEnabled) {
            manager(c).cancel(pi)
            return
        }
        val at = nextOccurrence(s.bedtimeHour, s.bedtimeMinute, s.bedtimeDays, s.bedtimeReminderMinutes)
        manager(c).setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    private fun nextOccurrence(hour: Int, minute: Int, days: Set<Int>, minusMinutes: Int): Long {
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now()
        val time = LocalTime.of(hour, minute).minusMinutes(minusMinutes.toLong())
        var date: LocalDate = if (now.toLocalTime() < time) now.toLocalDate() else now.toLocalDate().plusDays(1)
        repeat(8) {
            if (days.isEmpty() || date.dayOfWeek.value in days) {
                return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
            }
            date = date.plusDays(1)
        }
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }
}

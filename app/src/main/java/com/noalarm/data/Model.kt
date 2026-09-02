package com.noalarm.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class KeyAction { NONE, SNOOZE, DISMISS, VOLUME }

data class Alarm(
    val id: Long,
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** ISO: 1 = lunedi' ... 7 = domenica. Vuoto = sveglia singola. */
    val days: Set<Int> = emptySet(),
    val label: String = "",
    /** null = suoneria sveglia di sistema. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val gradualVolume: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** 0 = non silenziare mai. */
    val autoSilenceMinutes: Int = 10,
    val glyph: Boolean = true,
    /** Salta la prossima occorrenza (equivalente di "Ignora sveglia" di Google Clock). */
    val skipNext: Boolean = false,
    /** > 0 quando la sveglia e' posticipata: istante del prossimo squillo. */
    val snoozedUntil: Long = 0L,
) {
    val repeating get() = days.isNotEmpty()

    /** Prossimo istante di squillo in millis, o null se non ne ha uno. */
    fun nextTrigger(now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!enabled) return null
        if (snoozedUntil > 0) return snoozedUntil
        val time = LocalTime.of(hour, minute)
        var date: LocalDate = if (now.toLocalTime() < time) now.toLocalDate() else now.toLocalDate().plusDays(1)
        if (days.isEmpty()) {
            if (skipNext) date = date.plusDays(1)
            return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        var skipped = !skipNext
        for (i in 0..14) {
            val d = date.plusDays(i.toLong())
            if (d.dayOfWeek.value in days) {
                if (skipped) return d.atTime(time).atZone(zone).toInstant().toEpochMilli()
                skipped = true
            }
        }
        return null
    }
}

data class TimerItem(
    val id: Long,
    val label: String = "",
    val totalMs: Long,
    /** Istante di scadenza quando [running], altrimenti 0. */
    val endAt: Long = 0L,
    /** Residuo congelato quando in pausa. */
    val remainingMs: Long = totalMs,
    val running: Boolean = false,
    val firedAt: Long = 0L,
) {
    fun remaining(now: Long = System.currentTimeMillis()): Long =
        if (running) endAt - now else remainingMs

    val expired get() = firedAt > 0
}

data class Settings(
    val use24h: Boolean = true,
    val weekStartsMonday: Boolean = true,
    val showSeconds: Boolean = false,
    val defaultSnoozeMinutes: Int = 10,
    val defaultAutoSilenceMinutes: Int = 10,
    val volumeKeyAction: KeyAction = KeyAction.SNOOZE,
    val powerKeyAction: KeyAction = KeyAction.DISMISS,
    val flipAction: KeyAction = KeyAction.NONE,
    val shakeAction: KeyAction = KeyAction.NONE,
    val glyphEnabled: Boolean = true,
    val glyphToyClock: Boolean = true,
    /** Passo dei pulsanti +/- che regolano il rinvio mentre la sveglia suona (stile Samsung). */
    val snoozeStepMinutes: Int = 1,
    val snoozeMinMinutes: Int = 1,
    val snoozeMaxMinutes: Int = 60,
    val snoozeLimit: Int = 0, // 0 = illimitato
    val timerSoundUri: String? = null,
    val worldClocks: List<String> = emptyList(),
    val homeZone: String = "",
    val bedtimeEnabled: Boolean = false,
    val bedtimeHour: Int = 23,
    val bedtimeMinute: Int = 0,
    val wakeHour: Int = 7,
    val wakeMinute: Int = 0,
    val bedtimeDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val bedtimeReminderMinutes: Int = 30,
) {
    fun dayOrder(): List<DayOfWeek> {
        val all = DayOfWeek.values().toList()
        return if (weekStartsMonday) all else listOf(DayOfWeek.SUNDAY) + all.dropLast(1)
    }
}

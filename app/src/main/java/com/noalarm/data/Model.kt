package com.noalarm.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

enum class KeyAction { NONE, SNOOZE, DISMISS, VOLUME }

/** Cosa disegna la Glyph Matrix mentre questa sveglia suona. */
enum class GlyphStyle { CYCLE, CLOCK, BELL, LABEL, COUNTDOWN }

data class Alarm(
    val id: Long,
    val hour: Int = 7,
    val minute: Int = 0,
    val enabled: Boolean = true,
    /** ISO: 1 = lunedi' ... 7 = domenica. Vuoto = sveglia singola. */
    val days: Set<Int> = emptySet(),
    /** > 0: suona una volta sola in questa data (epoch day). Ha la precedenza su [days]. */
    val dateEpochDay: Long = 0L,
    /** Date (epoch day) in cui questa sveglia non deve suonare. */
    val skipDates: Set<Long> = emptySet(),
    val label: String = "",
    /** null = suoneria sveglia di sistema. */
    val soundUri: String? = null,
    val vibrate: Boolean = true,
    val gradualVolume: Boolean = true,
    val snoozeMinutes: Int = 10,
    /** 0 = non silenziare mai. */
    val autoSilenceMinutes: Int = 10,
    // Regolazione del rinvio con i pulsanti +/- mentre suona: per singola sveglia.
    val snoozeStepMinutes: Int = 1,
    val snoozeMinMinutes: Int = 1,
    val snoozeMaxMinutes: Int = 60,
    /** 0 = rinvii illimitati. */
    val snoozeLimit: Int = 0,
    val glyph: Boolean = true,
    val glyphStyle: GlyphStyle = GlyphStyle.CYCLE,
    /** Salta la prossima occorrenza (equivalente di "Ignora sveglia" di Google Clock). */
    val skipNext: Boolean = false,
    /** > 0 quando la sveglia e' posticipata: istante del prossimo squillo. */
    val snoozedUntil: Long = 0L,
) {
    val repeating get() = days.isNotEmpty() && dateEpochDay == 0L
    val onDate get() = dateEpochDay > 0L
    val date: LocalDate? get() = if (onDate) LocalDate.ofEpochDay(dateEpochDay) else null

    /** Prossimo istante di squillo in millis, o null se non ne ha uno. */
    fun nextTrigger(now: LocalDateTime = LocalDateTime.now(), zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!enabled) return null
        if (snoozedUntil > 0) return snoozedUntil
        val time = LocalTime.of(hour, minute)
        val from = if (now.toLocalTime() < time) now.toLocalDate() else now.toLocalDate().plusDays(1)

        if (onDate) {
            val d = LocalDate.ofEpochDay(dateEpochDay)
            if (d < from || d.toEpochDay() in skipDates) return null
            return d.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }

        var toSkip = if (skipNext) 1 else 0
        // Un anno di margine: oltre, la ripetizione non esiste.
        for (i in 0..370L) {
            val d = from.plusDays(i)
            if (days.isNotEmpty() && d.dayOfWeek.value !in days) continue
            if (d.toEpochDay() in skipDates) continue
            if (toSkip > 0) {
                toSkip--
                continue
            }
            return d.atTime(time).atZone(zone).toInstant().toEpochMilli()
        }
        return null
    }

    /**
     * Se questa sveglia e' prevista in [date]. Serve al calendario, che proietta
     * le ripetizioni sui giorni del mese.
     */
    fun firesOn(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Boolean {
        if (!enabled || date.toEpochDay() in skipDates) return false
        if (onDate) return date.toEpochDay() == dateEpochDay
        if (days.isNotEmpty()) {
            if (date.dayOfWeek.value !in days) return false
            // Una ripetizione non vale per i giorni gia' passati.
            val next = nextTrigger(zone = zone) ?: return false
            return !date.isBefore(java.time.Instant.ofEpochMilli(next).atZone(zone).toLocalDate())
        }
        // Sveglia singola senza data: cade solo nel giorno del prossimo squillo.
        val next = nextTrigger(zone = zone) ?: return false
        return date == java.time.Instant.ofEpochMilli(next).atZone(zone).toLocalDate()
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

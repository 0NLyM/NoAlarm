package com.noalarm

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

object Format {

    fun hhmm(hour: Int, minute: Int, use24h: Boolean): String =
        if (use24h) "%02d:%02d".format(hour, minute)
        else "%d:%02d".format(if (hour % 12 == 0) 12 else hour % 12, minute)

    fun amPm(hour: Int, use24h: Boolean): String = if (use24h) "" else if (hour < 12) "AM" else "PM"

    fun clock(at: ZonedDateTime, use24h: Boolean, seconds: Boolean = false): String {
        val base = hhmm(at.hour, at.minute, use24h)
        return if (seconds) "%s:%02d".format(base, at.second) else base
    }

    /** "1:23:45" oppure "12:34" per i timer. */
    fun timer(ms: Long): String {
        val t = (if (ms > 0) ms + 999 else 0L) / 1000
        val h = t / 3600
        val m = (t % 3600) / 60
        val s = t % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
    }

    /** "01:23.45" per il cronometro. */
    fun stopwatch(ms: Long): String {
        val cs = ms / 10
        val h = cs / 360000
        val m = (cs % 360000) / 6000
        val s = (cs % 6000) / 100
        return if (h > 0) "%d:%02d:%02d.%02d".format(h, m, s, cs % 100)
        else "%02d:%02d.%02d".format(m, s, cs % 100)
    }

    /** Distanza da adesso: "fra 7 h e 12 min". */
    fun until(at: Long, now: Long = System.currentTimeMillis()): String {
        val min = ((at - now).coerceAtLeast(0) + 59_999) / 60_000
        val d = min / 1440
        val h = (min % 1440) / 60
        val m = min % 60
        return when {
            d > 0 -> "fra ${d}g ${h}h"
            h > 0 && m > 0 -> "fra ${h}h e ${m}min"
            h > 0 -> "fra ${h}h"
            else -> "fra ${m}min"
        }
    }

    fun dayLabel(day: DayOfWeek, short: Boolean = true): String =
        day.getDisplayName(if (short) TextStyle.SHORT else TextStyle.FULL, Locale.getDefault())
            .replaceFirstChar { it.uppercase() }.trimEnd('.')

    fun daysLabel(days: Set<Int>, order: List<DayOfWeek>): String = when {
        days.isEmpty() -> "Una volta"
        days.size == 7 -> "Tutti i giorni"
        days == setOf(1, 2, 3, 4, 5) -> "Giorni feriali"
        days == setOf(6, 7) -> "Fine settimana"
        else -> order.filter { it.value in days }.joinToString(" ") { dayLabel(it).take(3) }
    }

    /** "New York" da "America/New_York". */
    fun zoneCity(id: String): String =
        id.substringAfterLast('/').replace('_', ' ')

    fun zoneRegion(id: String): String =
        id.substringBeforeLast('/', "").replace('_', ' ')

    /** Scarto orario rispetto alla zona locale: "+5h30". */
    fun zoneOffset(id: String, now: Instant = Instant.now()): String {
        val here = ZoneId.systemDefault().rules.getOffset(now).totalSeconds
        val there = ZoneId.of(id).rules.getOffset(now).totalSeconds
        val diff = (there - here) / 60
        if (diff == 0) return "Stessa ora"
        val sign = if (diff > 0) "+" else "-"
        val h = abs(diff) / 60
        val m = abs(diff) % 60
        return if (m == 0) "$sign${h}h" else "$sign${h}h$m"
    }

    /** "Oggi" / "Domani" / "Mar" per il giorno di uno squillo. */
    fun dayOf(at: Long): String {
        val zone = ZoneId.systemDefault()
        val today = ZonedDateTime.now(zone).toLocalDate()
        val date = Instant.ofEpochMilli(at).atZone(zone).toLocalDate()
        return when (date.toEpochDay() - today.toEpochDay()) {
            0L -> "Oggi"
            1L -> "Domani"
            else -> dayLabel(date.dayOfWeek)
        }
    }

    fun midnightMs(t: LocalTime): Long = t.toSecondOfDay() * 1000L
}

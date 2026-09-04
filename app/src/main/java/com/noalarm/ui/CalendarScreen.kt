package com.noalarm.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.data.Alarm
import com.noalarm.data.Store
import java.time.LocalDate
import java.time.YearMonth

/**
 * Calendario delle sveglie: proietta sui giorni del mese le ripetizioni
 * settimanali e le sveglie legate a una data. Da qui si aprono, si saltano
 * per un solo giorno e se ne creano di nuove valide una volta sola.
 */
@Composable
fun CalendarScreen() {
    val context = LocalContext.current
    val alarms by Store.alarms.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()

    val today = remember { LocalDate.now() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf<Alarm?>(null) }

    val order = settings.dayOrder()
    // Le 42 celle della griglia, dal lunedi' (o domenica) che precede il primo del mese.
    val cells = remember(month, order.first()) {
        val first = month.atDay(1)
        val offset = order.indexOf(first.dayOfWeek)
        List(42) { first.plusDays((it - offset).toLong()) }
    }
    val ofDay = remember(alarms, cells) {
        cells.associateWith { d -> alarms.filter { it.firesOn(d) }.sortedWith(compareBy({ it.hour }, { it.minute })) }
    }
    val chosen = ofDay[selected].orEmpty()

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        Format.monthLabel(month).uppercase(),
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    DotIconButton(
                        Icons.Outlined.FiberManualRecord,
                        if (settings.showRepeatingDots) "Nascondi i pallini delle sveglie ripetute"
                        else "Mostra i pallini delle sveglie ripetute",
                        { Store.update { it.copy(showRepeatingDots = !it.showRepeatingDots) } },
                        size = 40,
                        contentColor = if (settings.showRepeatingDots) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.size(8.dp))
                    DotIconButton(Icons.Outlined.ChevronLeft, "Mese precedente",
                        { month = month.minusMonths(1) }, size = 40)
                    Spacer(Modifier.size(8.dp))
                    DotIconButton(Icons.Outlined.Today, "Oggi",
                        { month = YearMonth.from(today); selected = today }, size = 40)
                    Spacer(Modifier.size(8.dp))
                    DotIconButton(Icons.Outlined.ChevronRight, "Mese successivo",
                        { month = month.plusMonths(1) }, size = 40)
                }
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    order.forEach { d ->
                        Text(
                            Format.dayLabel(d).take(1).uppercase(),
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(6) { week ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    for (i in 0 until 7) {
                        val day = cells[week * 7 + i]
                        Box(Modifier.weight(1f)) {
                            DayCell(
                                day = day,
                                inMonth = YearMonth.from(day) == month,
                                isToday = day == today,
                                isSelected = day == selected,
                                alarms = ofDay[day].orEmpty(),
                                showRepeatingDots = settings.showRepeatingDots,
                                onClick = { selected = day },
                            )
                        }
                    }
                }
            }

            item {
                SectionLabel(
                    if (chosen.isEmpty()) "${Format.dateLabel(selected)} · nessuna sveglia"
                    else Format.dateLabel(selected)
                )
            }

            items(chosen.size) { i ->
                val alarm = chosen[i]
                val skipped = selected.toEpochDay() in alarm.skipDates
                Box(Modifier.padding(horizontal = 16.dp)) {
                    RowItem(
                        title = Format.hhmm(alarm.hour, alarm.minute, settings.use24h),
                        subtitle = buildString {
                            append(alarm.date?.let { "Solo in questa data" }
                                ?: Format.daysLabel(alarm.days, order))
                            if (alarm.label.isNotBlank()) append(" · ${alarm.label}")
                            if (skipped) append(" · saltata")
                        },
                        onClick = { editing = alarm },
                        trailing = {
                            DotIconButton(
                                if (skipped) Icons.Outlined.EventAvailable else Icons.Outlined.EventBusy,
                                if (skipped) "Ripristina in questa data" else "Salta in questa data",
                                {
                                    val day = selected.toEpochDay()
                                    AlarmScheduler.save(
                                        context,
                                        alarm.copy(
                                            skipDates = if (skipped) alarm.skipDates - day
                                            else alarm.skipDates + day
                                        ),
                                    )
                                },
                                size = 40,
                                contentColor = if (skipped) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                    )
                }
            }
        }

        FloatingActionButton(
            onClick = {
                editing = Alarm(
                    id = System.currentTimeMillis(),
                    dateEpochDay = selected.toEpochDay(),
                    snoozeMinutes = settings.defaultSnoozeMinutes,
                    autoSilenceMinutes = settings.defaultAutoSilenceMinutes,
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 104.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) { Icon(Icons.Outlined.Add, "Nuova sveglia il ${Format.dateLabel(selected)}") }
    }

    editing?.let { AlarmEditSheet(it) { editing = null } }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    alarms: List<Alarm>,
    showRepeatingDots: Boolean,
    onClick: () -> Unit,
) = Box(
    Modifier
        .aspectRatio(1f)
        .padding(2.dp)
        .clip(RoundedCornerShape(4.dp))
        .background(
            if (isSelected) MaterialTheme.colorScheme.secondary
            else MaterialTheme.colorScheme.surfaceContainer
        )
        .border(
            1.dp,
            if (isToday) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.outline,
            RoundedCornerShape(4.dp),
        )
        .clickable(onClick = onClick),
    contentAlignment = Alignment.Center,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            day.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodySmall,
            color = when {
                isSelected -> MaterialTheme.colorScheme.onSecondary
                inMonth -> MaterialTheme.colorScheme.onSurface
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(3.dp))
        // Un punto per sveglia, fino a tre: la stessa grammatica del dot-matrix.
        // Le ripetute sono rosse (si puo' nascondere), le singole seguono il tema.
        val dots = alarms.filter { !it.repeating || showRepeatingDots }.take(3)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            dots.forEach { alarm ->
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(
                            if (alarm.repeating) MaterialTheme.colorScheme.error
                            else if (isSelected) MaterialTheme.colorScheme.onSecondary
                            else MaterialTheme.colorScheme.secondary
                        )
                )
            }
        }
    }
}

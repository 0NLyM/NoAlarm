package com.noalarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.data.Alarm
import com.noalarm.data.Store
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalTime

@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val alarms by Store.alarms.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    var bedtime by remember { mutableStateOf(false) }
    rememberNow(30_000L)

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val next = AlarmScheduler.next()
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    if (next == null) {
                        Text(
                            "NESSUNA SVEGLIA ATTIVA",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            "${Format.dayOf(next.second)} · ${Format.until(next.second)}".uppercase(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.height(12.dp))
                        DotText(
                            Format.hhmm(next.first.hour, next.first.minute, settings.use24h),
                            Modifier.fillMaxWidth().height(84.dp),
                            cell = 11.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                            offColor = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }

            // Sveglia fissa della routine del sonno, come in Google Clock:
            // sta in cima all'elenco e apre la finestra Riposo.
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    BedtimeRow(onClick = { bedtime = true })
                }
            }

            items(alarms, key = { it.id }) { alarm ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    AlarmRow(
                        alarm = alarm,
                        use24h = settings.use24h,
                        order = settings.dayOrder(),
                        onToggle = {
                            AlarmScheduler.save(
                                context,
                                alarm.copy(enabled = it, snoozedUntil = 0L, skipNext = false),
                            )
                        },
                        onClick = { editing = alarm },
                    )
                }
            }

            if (alarms.isEmpty()) item {
                Text(
                    "Tocca + per creare la prima sveglia.",
                    Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        FloatingActionButton(
            onClick = {
                editing = Alarm(
                    id = System.currentTimeMillis(),
                    snoozeMinutes = settings.defaultSnoozeMinutes,
                    autoSilenceMinutes = settings.defaultAutoSilenceMinutes,
                )
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) { Icon(Icons.Outlined.Add, "Nuova sveglia") }
    }

    editing?.let { AlarmEditSheet(it) { editing = null } }
    if (bedtime) BedtimeSheet { bedtime = false }
}

/** Riga fissa in cima all'elenco: apre la routine del sonno. */
@Composable
private fun BedtimeRow(onClick: () -> Unit) {
    val s by Store.settings.collectAsStateWithLifecycle()
    val sleep = Duration.between(
        LocalTime.of(s.bedtimeHour, s.bedtimeMinute),
        LocalTime.of(s.wakeHour, s.wakeMinute),
    ).let { if (it.isNegative) it.plusHours(24) else it }

    Panel(onClick = onClick) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Bedtime,
                "Routine del sonno",
                tint = if (s.bedtimeEnabled) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text(
                    "%s → %s".format(
                        Format.hhmm(s.bedtimeHour, s.bedtimeMinute, s.use24h),
                        Format.hhmm(s.wakeHour, s.wakeMinute, s.use24h),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    if (s.bedtimeEnabled) "%dh%02d di sonno · %s".format(
                        sleep.toHours(), sleep.toMinutes() % 60,
                        Format.daysLabel(s.bedtimeDays, s.dayOrder()),
                    ) else "Routine del sonno disattivata",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BedtimeSheet(onDismiss: () -> Unit) = ModalBottomSheet(
    onDismissRequest = onDismiss,
    sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    containerColor = MaterialTheme.colorScheme.background,
) { BedtimeScreen() }

/** Foglio di modifica di una sveglia, condiviso con la schermata Calendario. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmEditSheet(alarm: Alarm, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val settings by Store.settings.collectAsStateWithLifecycle()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        AlarmEditor(
            alarm = alarm,
            use24h = settings.use24h,
            order = settings.dayOrder(),
            onSave = { AlarmScheduler.save(context, it); onDismiss() },
            onDelete = { AlarmScheduler.delete(context, alarm.id); onDismiss() },
        )
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    use24h: Boolean,
    order: List<DayOfWeek>,
    onToggle: (Boolean) -> Unit,
    onClick: () -> Unit,
) = Panel(onClick = onClick) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            DotText(
                Format.hhmm(alarm.hour, alarm.minute, use24h),
                Modifier.height(40.dp).fillMaxWidth(0.75f),
                cell = 5.dp,
                color = if (alarm.enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                offColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                buildString {
                    append(alarm.date?.let(Format::dateLabel) ?: Format.daysLabel(alarm.days, order))
                    if (alarm.label.isNotBlank()) append(" · ${alarm.label}")
                    if (alarm.snoozedUntil > 0) append(" · posticipata")
                    if (alarm.skipNext) append(" · saltata una volta")
                    if (alarm.enabled && alarm.nextTrigger() == null) append(" · scaduta")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = alarm.enabled,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
            ),
        )
    }
}

@Composable
private fun AlarmEditor(
    alarm: Alarm,
    use24h: Boolean,
    order: List<DayOfWeek>,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(alarm.id) { mutableStateOf(alarm) }

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            draft = draft.copy(soundUri = uri?.toString())
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Column(
            // fill = false: la barra dei comandi resta sempre visibile in basso,
            // qualunque sia l'altezza del contenuto.
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DotTimePicker(
                hour = draft.hour,
                minute = draft.minute,
                use24h = use24h,
                onChange = { h, m -> draft = draft.copy(hour = h, minute = m) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )

            if (draft.onDate) {
                RowItem(
                    title = "Data singola",
                    subtitle = draft.date?.let(Format::dateLabel),
                    trailing = {
                        DotIconButton(
                            Icons.Outlined.Close,
                            "Torna alla ripetizione settimanale",
                            { draft = draft.copy(dateEpochDay = 0L) },
                            size = 40,
                        )
                    },
                )
            } else {
                SectionLabel("Ripetizione")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    order.forEach { day ->
                        val on = day.value in draft.days
                        Box(Modifier.weight(1f)) {
                            DotButton(
                                label = Format.dayLabel(day).take(1),
                                size = 40,
                                color = if (on) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.surfaceContainerHigh,
                                contentColor = if (on) MaterialTheme.colorScheme.onSecondary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                onClick = {
                                    draft = draft.copy(
                                        days = if (on) draft.days - day.value else draft.days + day.value
                                    )
                                },
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.label,
                onValueChange = { draft = draft.copy(label = it.take(24)) },
                label = { Text("Etichetta") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            RowItem(
                title = "Suoneria",
                subtitle = ringtoneName(draft.soundUri),
                onClick = {
                    ringtone.launch(
                        Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                            .putExtra(
                                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                draft.soundUri?.let(Uri::parse)
                                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                            )
                    )
                },
            )

            SwitchRow("Vibrazione", draft.vibrate) { draft = draft.copy(vibrate = it) }
            SwitchRow("Volume crescente", draft.gradualVolume) { draft = draft.copy(gradualVolume = it) }
            SwitchRow("Glyph Matrix mentre suona", draft.glyph) { draft = draft.copy(glyph = it) }
            StepperRow(
                "Silenzia dopo",
                if (draft.autoSilenceMinutes == 0) "Mai" else "${draft.autoSilenceMinutes} min",
                0, 60, draft.autoSilenceMinutes,
            ) { draft = draft.copy(autoSilenceMinutes = it) }

            SectionLabel("Rinvio di questa sveglia")
            StepperRow("Minuti di partenza", "${draft.snoozeMinutes} min", 1, 60, draft.snoozeMinutes) {
                draft = draft.copy(snoozeMinutes = it)
            }
            StepperRow("Passo dei pulsanti", "${draft.snoozeStepMinutes} min", 1, 15, draft.snoozeStepMinutes) {
                draft = draft.copy(snoozeStepMinutes = it)
            }
            StepperRow("Minimo", "${draft.snoozeMinMinutes} min", 1, 30, draft.snoozeMinMinutes) {
                draft = draft.copy(
                    snoozeMinMinutes = it,
                    snoozeMaxMinutes = maxOf(it, draft.snoozeMaxMinutes),
                )
            }
            StepperRow("Massimo", "${draft.snoozeMaxMinutes} min", 1, 120, draft.snoozeMaxMinutes) {
                draft = draft.copy(snoozeMaxMinutes = maxOf(it, draft.snoozeMinMinutes))
            }
            StepperRow(
                "Numero massimo di rinvii",
                if (draft.snoozeLimit == 0) "Illimitati" else "${draft.snoozeLimit}",
                0, 10, draft.snoozeLimit,
            ) { draft = draft.copy(snoozeLimit = it) }

            Spacer(Modifier.height(8.dp))
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 12.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DotIconButton(
                Icons.Outlined.Delete,
                "Elimina",
                onDelete,
                size = 56,
                contentColor = MaterialTheme.colorScheme.error,
            )
            DotIconButton(
                Icons.Outlined.Check,
                "Salva",
                { onSave(draft.copy(enabled = true, snoozedUntil = 0L)) },
                size = 64,
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) = RowItem(
    title = title,
    onClick = { onChange(!checked) },
    trailing = {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = MaterialTheme.colorScheme.secondary,
                checkedThumbColor = MaterialTheme.colorScheme.onSecondary,
            ),
        )
    },
)

@Composable
fun StepperRow(title: String, value: String, min: Int, max: Int, current: Int, onChange: (Int) -> Unit) = RowItem(
    title = title,
    subtitle = value,
    trailing = {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DotIconButton(
                Icons.Outlined.Remove, "Diminuisci",
                { onChange((current - 1).coerceAtLeast(min)) },
                size = 40, enabled = current > min,
            )
            DotIconButton(
                Icons.Outlined.Add, "Aumenta",
                { onChange((current + 1).coerceAtMost(max)) },
                size = 40, enabled = current < max,
            )
        }
    },
)

@Composable
private fun ringtoneName(uri: String?): String {
    val context = LocalContext.current
    return remember(uri) {
        runCatching {
            RingtoneManager.getRingtone(
                context,
                uri?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
            ).getTitle(context)
        }.getOrNull() ?: "Suoneria predefinita"
    }
}

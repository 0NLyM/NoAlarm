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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlarmScreen() {
    val context = LocalContext.current
    val alarms by Store.alarms.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Alarm?>(null) }
    rememberNow(30_000L)

    fun persist(a: Alarm) {
        Store.putAlarm(a)
        AlarmScheduler.schedule(context, a)
        com.noalarm.alarm.NotificationHelper.showUpcoming(context, AlarmScheduler.next())
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val next = AlarmScheduler.next()
                Column(Modifier.fillMaxWidth().padding(24.dp)) {
                    if (next == null) {
                        Text("NESSUNA SVEGLIA ATTIVA", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            items(alarms, key = { it.id }) { alarm ->
                Box(Modifier.padding(horizontal = 16.dp)) {
                    AlarmRow(
                        alarm = alarm,
                        use24h = settings.use24h,
                        order = settings.dayOrder(),
                        onToggle = { persist(alarm.copy(enabled = it, snoozedUntil = 0L, skipNext = false)) },
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

    editing?.let { alarm ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.background,
        ) {
            AlarmEditor(
                alarm = alarm,
                use24h = settings.use24h,
                order = settings.dayOrder(),
                onSave = { persist(it); editing = null },
                onDelete = {
                    AlarmScheduler.cancel(context, alarm.id)
                    Store.removeAlarm(alarm.id)
                    com.noalarm.alarm.NotificationHelper.showUpcoming(context, AlarmScheduler.next())
                    editing = null
                },
            )
        }
    }
}

@Composable
private fun AlarmRow(
    alarm: Alarm,
    use24h: Boolean,
    order: List<java.time.DayOfWeek>,
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
                    append(Format.daysLabel(alarm.days, order))
                    if (alarm.label.isNotBlank()) append(" · ${alarm.label}")
                    if (alarm.snoozedUntil > 0) append(" · posticipata")
                    if (alarm.skipNext) append(" · saltata una volta")
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlarmEditor(
    alarm: Alarm,
    use24h: Boolean,
    order: List<java.time.DayOfWeek>,
    onSave: (Alarm) -> Unit,
    onDelete: () -> Unit,
) {
    var draft by remember(alarm.id) { mutableStateOf(alarm) }
    val picker = rememberTimePickerState(alarm.hour, alarm.minute, use24h)

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            draft = draft.copy(soundUri = uri?.toString())
        }
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(picker) }

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

        StepperRow("Rinvio", "${draft.snoozeMinutes} min", 1, 60, draft.snoozeMinutes) {
            draft = draft.copy(snoozeMinutes = it)
        }
        StepperRow(
            "Silenzia dopo",
            if (draft.autoSilenceMinutes == 0) "Mai" else "${draft.autoSilenceMinutes} min",
            0, 60, draft.autoSilenceMinutes,
        ) { draft = draft.copy(autoSilenceMinutes = it) }

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onDelete, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Outlined.Delete, "Elimina", tint = MaterialTheme.colorScheme.error)
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                DotButton(
                    "Salva",
                    onClick = {
                        onSave(
                            draft.copy(
                                hour = picker.hour,
                                minute = picker.minute,
                                enabled = true,
                                snoozedUntil = 0L,
                            )
                        )
                    },
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
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
            DotButton("-", { onChange((current - 1).coerceAtLeast(min)) }, size = 40, enabled = current > min)
            DotButton("+", { onChange((current + 1).coerceAtMost(max)) }, size = 40, enabled = current < max)
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

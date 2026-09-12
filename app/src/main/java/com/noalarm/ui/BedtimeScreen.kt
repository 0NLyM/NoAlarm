package com.noalarm.ui

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import java.time.Duration
import java.time.LocalTime

/** Routine del sonno. Si apre dalla riga fissa in cima alla schermata Sveglia. */
@Composable
fun BedtimeScreen() {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    val alarms by Store.alarms.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) }

    // La sveglia vera che la routine fa suonare: si personalizza qui come
    // qualunque altra sveglia, non solo orario e giorni.
    val wake = alarms.firstOrNull { it.id == AlarmScheduler.BEDTIME_WAKE_ID }
        ?: Alarm(
            id = AlarmScheduler.BEDTIME_WAKE_ID,
            hour = s.wakeHour,
            minute = s.wakeMinute,
            days = s.bedtimeDays,
            enabled = s.bedtimeEnabled,
            label = "Sveglia",
            reminderMinutes = 0,
        )
    fun updateWake(block: (Alarm) -> Alarm) = AlarmScheduler.save(context, block(wake))

    val ringtone = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            val uri: Uri? = r.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            updateWake { it.copy(soundUri = uri?.toString()) }
        }
    }

    val sleep = Duration.between(
        LocalTime.of(s.bedtimeHour, s.bedtimeMinute),
        LocalTime.of(s.wakeHour, s.wakeMinute),
    ).let { if (it.isNegative) it.plusHours(24) else it }

    Column(
        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotText(
                    "%dH%02d".format(sleep.toHours(), sleep.toMinutes() % 60),
                    Modifier.fillMaxWidth().height(64.dp),
                    cell = 9.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                Text("DI SONNO PROGRAMMATO", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        SwitchRow("Routine attiva", s.bedtimeEnabled) { on ->
            Store.update { it.copy(bedtimeEnabled = on) }
            AlarmScheduler.scheduleBedtime(context)
        }
        RowItem(
            title = "Ora di dormire",
            subtitle = Format.hhmm(s.bedtimeHour, s.bedtimeMinute, s.use24h),
            onClick = { editing = "bed" },
        )
        RowItem(
            title = "Sveglia",
            subtitle = Format.hhmm(s.wakeHour, s.wakeMinute, s.use24h),
            onClick = { editing = "wake" },
        )
        StepperRow("Promemoria prima", "${s.bedtimeReminderMinutes} min", 0, 120, s.bedtimeReminderMinutes) {
            Store.update { st -> st.copy(bedtimeReminderMinutes = it) }
            AlarmScheduler.scheduleBedtime(context)
        }

        SectionLabel("Giorni")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.dayOrder().forEach { day ->
                val on = day.value in s.bedtimeDays
                Box(Modifier.weight(1f)) {
                    DotButton(
                        label = Format.dayLabel(day).take(1),
                        size = 40,
                        color = if (on) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (on) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = {
                            Store.update {
                                it.copy(
                                    bedtimeDays = if (on) it.bedtimeDays - day.value
                                    else it.bedtimeDays + day.value
                                )
                            }
                            AlarmScheduler.scheduleBedtime(context)
                        },
                    )
                }
            }
        }

        SectionLabel("Quando suona")
        RowItem(
            title = "Suoneria",
            subtitle = ringtoneName(wake.soundUri),
            onClick = {
                ringtone.launch(
                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                        .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                        .putExtra(
                            RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                            wake.soundUri?.let(Uri::parse)
                                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                        )
                )
            },
        )
        SwitchRow("Vibrazione", wake.vibrate) { on -> updateWake { it.copy(vibrate = on) } }
        SwitchRow("Volume crescente", wake.gradualVolume) { on -> updateWake { it.copy(gradualVolume = on) } }
        SwitchRow("Glyph Matrix mentre suona", wake.glyph) { on -> updateWake { it.copy(glyph = on) } }
        if (wake.glyph) {
            SectionLabel("Cosa mostra la matrice")
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                GlyphStyle.entries.forEach { style ->
                    GlyphStyleCard(
                        style = style,
                        label = wake.label,
                        name = glyphStyleLabel(style),
                        selected = style == wake.glyphStyle,
                        onClick = { updateWake { it.copy(glyphStyle = style) } },
                    )
                }
            }
        }
        StepperRow(
            "Silenzia dopo",
            if (wake.autoSilenceMinutes == 0) "Mai" else "${wake.autoSilenceMinutes} min",
            0, 60, wake.autoSilenceMinutes,
        ) { updateWake { a -> a.copy(autoSilenceMinutes = it) } }

        SectionLabel("Rinvio di questa sveglia")
        StepperRow("Minuti di partenza", "${wake.snoozeMinutes} min", 1, 60, wake.snoozeMinutes) {
            updateWake { a -> a.copy(snoozeMinutes = it) }
        }
        StepperRow("Passo dei pulsanti", "${wake.snoozeStepMinutes} min", 1, 15, wake.snoozeStepMinutes) {
            updateWake { a -> a.copy(snoozeStepMinutes = it) }
        }
        StepperRow("Minimo", "${wake.snoozeMinMinutes} min", 1, 30, wake.snoozeMinMinutes) {
            updateWake { a -> a.copy(snoozeMinMinutes = it, snoozeMaxMinutes = maxOf(it, a.snoozeMaxMinutes)) }
        }
        StepperRow("Massimo", "${wake.snoozeMaxMinutes} min", 1, 120, wake.snoozeMaxMinutes) {
            updateWake { a -> a.copy(snoozeMaxMinutes = maxOf(it, a.snoozeMinMinutes)) }
        }
        StepperRow(
            "Numero massimo di rinvii",
            if (wake.snoozeLimit == 0) "Illimitati" else "${wake.snoozeLimit}",
            0, 10, wake.snoozeLimit,
        ) { updateWake { a -> a.copy(snoozeLimit = it) } }

        Spacer(Modifier.height(48.dp))
    }

    editing?.let { which ->
        var h by remember(which) { mutableIntStateOf(if (which == "bed") s.bedtimeHour else s.wakeHour) }
        var m by remember(which) { mutableIntStateOf(if (which == "bed") s.bedtimeMinute else s.wakeMinute) }
        AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                DotIconButton(
                    Icons.Outlined.Check, "Conferma",
                    {
                        Store.update {
                            if (which == "bed") it.copy(bedtimeHour = h, bedtimeMinute = m)
                            else it.copy(wakeHour = h, wakeMinute = m)
                        }
                        AlarmScheduler.scheduleBedtime(context)
                        editing = null
                    },
                    size = 56,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            },
            title = {
                Text(
                    if (which == "bed") "ORA DI DORMIRE" else "SVEGLIA",
                    style = MaterialTheme.typography.labelLarge,
                )
            },
            text = {
                DotTimePicker(h, m, s.use24h, { nh, nm -> h = nh; m = nm }, Modifier.fillMaxWidth())
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

package com.noalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
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
import com.noalarm.data.Store
import java.time.Duration
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BedtimeScreen() {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<String?>(null) }

    val sleep = Duration.between(
        LocalTime.of(s.bedtimeHour, s.bedtimeMinute),
        LocalTime.of(s.wakeHour, s.wakeMinute),
    ).let { if (it.isNegative) it.plusHours(24) else it }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DotText(
                    "%dH%02d".format(sleep.toHours(), sleep.toMinutes() % 60),
                    Modifier.fillMaxWidth().height(64.dp),
                    cell = 9.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                    offColor = MaterialTheme.colorScheme.outline,
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
        Spacer(Modifier.height(48.dp))
    }

    editing?.let { which ->
        val hour = if (which == "bed") s.bedtimeHour else s.wakeHour
        val minute = if (which == "bed") s.bedtimeMinute else s.wakeMinute
        val state = rememberTimePickerState(hour, minute, s.use24h)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { editing = null },
            confirmButton = {
                DotButton("Ok", {
                    Store.update {
                        if (which == "bed") it.copy(bedtimeHour = state.hour, bedtimeMinute = state.minute)
                        else it.copy(wakeHour = state.hour, wakeMinute = state.minute)
                    }
                    AlarmScheduler.scheduleBedtime(context)
                    editing = null
                }, size = 56)
            },
            text = { TimePicker(state) },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }
}

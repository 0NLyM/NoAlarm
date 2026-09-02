package com.noalarm.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.BuildConfig
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.data.KeyAction
import com.noalarm.data.Store

private fun label(a: KeyAction) = when (a) {
    KeyAction.NONE -> "Niente"
    KeyAction.SNOOZE -> "Posticipa"
    KeyAction.DISMISS -> "Spegni"
    KeyAction.VOLUME -> "Volume"
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SectionLabel("Orologio")
        SwitchRow("Formato 24 ore", s.use24h) { v -> Store.update { it.copy(use24h = v) } }
        SwitchRow("Mostra i secondi", s.showSeconds) { v -> Store.update { it.copy(showSeconds = v) } }
        SwitchRow("La settimana inizia di lunedi'", s.weekStartsMonday) { v ->
            Store.update { it.copy(weekStartsMonday = v) }
        }

        SectionLabel("Sveglia")
        StepperRow("Rinvio predefinito", "${s.defaultSnoozeMinutes} min", 1, 60, s.defaultSnoozeMinutes) { v ->
            Store.update { it.copy(defaultSnoozeMinutes = v) }
        }
        StepperRow(
            "Silenzia dopo",
            if (s.defaultAutoSilenceMinutes == 0) "Mai" else "${s.defaultAutoSilenceMinutes} min",
            0, 60, s.defaultAutoSilenceMinutes,
        ) { v -> Store.update { it.copy(defaultAutoSilenceMinutes = v) } }
        StepperRow("Limite rinvii", if (s.snoozeLimit == 0) "Illimitati" else "${s.snoozeLimit}", 0, 10, s.snoozeLimit) { v ->
            Store.update { it.copy(snoozeLimit = v) }
        }

        SectionLabel("Rinvio regolabile mentre suona")
        StepperRow("Passo dei pulsanti", "${s.snoozeStepMinutes} min", 1, 15, s.snoozeStepMinutes) { v ->
            Store.update { it.copy(snoozeStepMinutes = v) }
        }
        StepperRow("Minimo", "${s.snoozeMinMinutes} min", 1, 30, s.snoozeMinMinutes) { v ->
            Store.update { it.copy(snoozeMinMinutes = v, snoozeMaxMinutes = maxOf(v, it.snoozeMaxMinutes)) }
        }
        StepperRow("Massimo", "${s.snoozeMaxMinutes} min", 1, 120, s.snoozeMaxMinutes) { v ->
            Store.update { it.copy(snoozeMaxMinutes = maxOf(v, it.snoozeMinMinutes)) }
        }

        SectionLabel("Comandi mentre suona")
        CycleRow("Tasti volume", s.volumeKeyAction, KeyAction.entries.toList()) { v ->
            Store.update { it.copy(volumeKeyAction = v) }
        }
        CycleRow(
            "Tasto di accensione",
            s.powerKeyAction,
            listOf(KeyAction.NONE, KeyAction.SNOOZE, KeyAction.DISMISS),
        ) { v -> Store.update { it.copy(powerKeyAction = v) } }
        CycleRow(
            "Capovolgi il telefono",
            s.flipAction,
            listOf(KeyAction.NONE, KeyAction.SNOOZE, KeyAction.DISMISS),
        ) { v -> Store.update { it.copy(flipAction = v) } }
        CycleRow(
            "Scuoti il telefono",
            s.shakeAction,
            listOf(KeyAction.NONE, KeyAction.SNOOZE, KeyAction.DISMISS),
        ) { v -> Store.update { it.copy(shakeAction = v) } }

        SectionLabel("Glyph Matrix")
        SwitchRow("Usa la matrice mentre suona", s.glyphEnabled) { v ->
            Store.update { it.copy(glyphEnabled = v) }
        }
        Text(
            "Richiede un Nothing Phone (3). Sugli altri dispositivi resta inattiva.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Sistema")
        if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.canScheduleExact(context)) {
            RowItem(
                title = "Consenti sveglie esatte",
                subtitle = "Senza questo permesso le sveglie possono ritardare",
                onClick = {
                    context.startActivity(
                        Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            .setData(Uri.parse("package:${context.packageName}"))
                    )
                },
            )
        }
        RowItem(
            title = "Ottimizzazione batteria",
            subtitle = "Escludi NoAlarm per non perdere nessuna sveglia",
            onClick = {
                runCatching {
                    context.startActivity(Intent(AndroidSettings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }
            },
        )
        RowItem(
            title = "Notifiche",
            subtitle = "Canali, suoni e priorita'",
            onClick = {
                context.startActivity(
                    Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
                )
            },
        )
        RowItem(title = "Versione", subtitle = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        Spacer(Modifier.height(48.dp))
    }
}

/** Riga che cicla fra i valori possibili a ogni tocco: niente menu, niente dialog. */
@Composable
private fun CycleRow(title: String, current: KeyAction, options: List<KeyAction>, onPick: (KeyAction) -> Unit) = RowItem(
    title = title,
    subtitle = label(current),
    onClick = { onPick(options[(options.indexOf(current) + 1).mod(options.size)]) },
)

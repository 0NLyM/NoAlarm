package com.noalarm.ui

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.BuildConfig
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.alarm.AlarmService
import com.noalarm.alarm.NotificationHelper
import com.noalarm.data.Alarm
import com.noalarm.data.AppFont
import com.noalarm.data.BarAppearance
import com.noalarm.data.KeyAction
import com.noalarm.data.Store

private fun label(a: KeyAction) = when (a) {
    KeyAction.NONE -> "Niente"
    KeyAction.SNOOZE -> "Posticipa"
    KeyAction.DISMISS -> "Spegni"
    KeyAction.VOLUME -> "Volume"
}

private fun label(a: BarAppearance) = when (a) {
    BarAppearance.SOLID -> "Solido"
    BarAppearance.BLUR -> "Sfocato"
}

private fun label(f: AppFont) = when (f) {
    AppFont.SYSTEM -> "Sistema"
    AppFont.GEIST -> "Geist"
    AppFont.INTER -> "Inter"
}

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val s by Store.settings.collectAsStateWithLifecycle()
    var glyphTest by remember { mutableStateOf(false) }
    var wearDiagnostics by remember { mutableStateOf<String?>(null) }
    var backupResult by remember { mutableStateOf<String?>(null) }
    val exportBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        backupResult = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Store.exportJson().toByteArray()) }
        }.fold({ "Configurazione esportata." }, { "Esportazione fallita." })
    }
    val importBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val raw = runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        }.getOrNull()
        backupResult = if (raw != null && Store.importJson(raw)) "Configurazione importata." else "File non valido: nessuna modifica."
    }

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
        SwitchRow("Suona anche sull'orologio", s.defaultRingOnWatch) { v ->
            Store.update { it.copy(defaultRingOnWatch = v) }
        }
        Text(
            "Valgono per le sveglie nuove. Rinvio, passo dei pulsanti, minimo, massimo, "
                + "numero di rinvii e l'eco sul watch si regolano dentro ogni singola sveglia.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        RowItem(
            title = "Prova la sveglia adesso",
            subtitle = "Fa suonare una sveglia vera per controllare notifica, eco sul watch e Posticipa/Spegni",
            onClick = {
                Store.putAlarm(
                    Alarm(id = AlarmScheduler.TEST_ID, label = "Prova", soundUri = Alarm.SOUND_NONE, vibrate = false)
                )
                AlarmService.ring(context, AlarmScheduler.TEST_ID)
                wearDiagnostics = collectWearDiagnostics(context)
            },
        )
        wearDiagnostics?.let {
            Text(
                it,
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
        RowItem(
            title = "Prova la matrice",
            subtitle = "Accende la Glyph e mostra cosa risponde il dispositivo",
            onClick = { glyphTest = true },
        )
        Text(
            "Richiede un Nothing Phone (3). Sugli altri dispositivi resta inattiva.",
            Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        SectionLabel("Aspetto")
        RowItem(
            title = "Switch e menu inferiore",
            subtitle = label(s.barAppearance),
            onClick = {
                val next = BarAppearance.entries[(s.barAppearance.ordinal + 1) % BarAppearance.entries.size]
                Store.update { it.copy(barAppearance = next) }
            },
        )
        RowItem(
            title = "Carattere",
            subtitle = label(s.fontFamily),
            onClick = {
                val next = AppFont.entries[(s.fontFamily.ordinal + 1) % AppFont.entries.size]
                Store.update { it.copy(fontFamily = next) }
            },
        )

        SectionLabel("Backup")
        RowItem(
            title = "Esporta configurazione",
            subtitle = "Sveglie e impostazioni in un file .json",
            onClick = { exportBackup.launch("noalarm-backup.json") },
        )
        RowItem(
            title = "Importa configurazione",
            subtitle = "Sostituisce sveglie e impostazioni attuali",
            onClick = { importBackup.launch(arrayOf("application/json")) },
        )
        backupResult?.let {
            Text(
                it,
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionLabel("Sistema")
        if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.canScheduleExact(context)) {
            RowItem(
                title = "Consenti sveglie esatte",
                subtitle = "Senza questo permesso le sveglie possono ritardare",
                onClick = { context.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)) },
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

    if (glyphTest) GlyphTestSheet { glyphTest = false }
}

/**
 * Cosa puo' impedire il bridging della notifica verso il watch, letto dal
 * telefono senza permessi nuovi: nessuno di questi e' garantito essere LA
 * causa, ma insieme dicono se il problema e' a monte (notifiche disattivate
 * o canale abbassato d'importanza) o nell'app che dovrebbe fare da ponte
 * (Mobvoi Health e simili si affidano quasi sempre a un proprio
 * NotificationListenerService, non al bridging nativo di Google — se non
 * ha "Accesso alle notifiche" concesso, non specchia nulla di nessuna app).
 */
private fun collectWearDiagnostics(c: Context): String {
    val nm = NotificationManagerCompat.from(c)
    val importance = c.getSystemService(NotificationManager::class.java)
        .getNotificationChannel(NotificationHelper.CH_ALARM)?.importance
    val bluetoothOn = runCatching { c.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled }.getOrNull()
    val listeners = NotificationManagerCompat.getEnabledListenerPackages(c)
    return "Notifiche attive: ${nm.areNotificationsEnabled()}\n" +
        "Importanza canale sveglia: $importance (4 = alta, quella impostata)\n" +
        "Bluetooth attivo: ${bluetoothOn ?: "sconosciuto"}\n" +
        "App con accesso alle notifiche: ${listeners.ifEmpty { setOf("nessuna") }.joinToString()}"
}

/** Riga che cicla fra i valori possibili a ogni tocco: niente menu, niente dialog. */
@Composable
private fun CycleRow(title: String, current: KeyAction, options: List<KeyAction>, onPick: (KeyAction) -> Unit) = RowItem(
    title = title,
    subtitle = label(current),
    onClick = { onPick(options[(options.indexOf(current) + 1).mod(options.size)]) },
)

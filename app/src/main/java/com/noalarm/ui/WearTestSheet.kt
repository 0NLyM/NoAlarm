package com.noalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.wear.WearBridge

/**
 * Prova della connessione con il watch: manda un'eco di prova e mostra passo
 * per passo dove si ferma, invece di scoprirlo solo quando una sveglia vera
 * non arriva.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearTestSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val status by WearBridge.status.collectAsStateWithLifecycle()

    // Se la prova e' ancora in corso quando si chiude il foglio, ferma anche l'eco sul watch.
    DisposableEffect(Unit) { onDispose { WearBridge.stopOnWatches(context) } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "Tieni il watch acceso e vicino al telefono, poi avvia la prova: " +
                    "dovrebbe vibrare e mostrare una schermata di prova.",
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RowItem(
                title = "Permesso Bluetooth",
                subtitle = when (status.permission) {
                    null -> "Non ancora verificato"
                    true -> "Concesso"
                    false -> "Negato: concedilo dalle impostazioni di sistema dell'app"
                },
            )
            RowItem(
                title = "Dispositivi accoppiati",
                subtitle = if (status.bondedDevices.isEmpty()) "Nessuno"
                else status.bondedDevices.joinToString(),
            )
            RowItem(
                title = "Connessione",
                subtitle = when {
                    status.connectedTo != null -> "${status.connectedTo} · ${status.method}"
                    status.checking -> "In corso..."
                    else -> "Non riuscita"
                },
            )
            RowItem(
                title = "Tempo di connessione",
                subtitle = status.elapsedMs?.let { "$it ms" } ?: "-",
            )
            RowItem(
                title = "Messaggio inviato",
                subtitle = when (status.sent) {
                    null -> "-"
                    true -> "Si'"
                    false -> "No"
                },
            )
            RowItem(
                title = "Risposta dal watch",
                subtitle = when (status.ackReceived) {
                    null -> "-"
                    true -> "Ricevuta in ${status.ackMs} ms"
                    false -> "Nessuna: l'app sul watch e' aggiornata e in esecuzione?"
                },
            )
            RowItem("Ultimo errore", status.lastError ?: "Nessuno")

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (status.checking) DotIconButton(
                    Icons.Outlined.Stop, "Ferma la prova",
                    { WearBridge.stopOnWatches(context) },
                    size = 72,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) else DotIconButton(
                    Icons.Outlined.PlayArrow, "Avvia la prova",
                    { WearBridge.test(context) },
                    size = 72,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

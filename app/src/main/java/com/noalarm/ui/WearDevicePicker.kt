package com.noalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.noalarm.wear.BondedDevice
import com.noalarm.wear.WearBridge

/**
 * Scelta manuale del watch per l'eco: un solo tentativo di connessione diretto
 * a quello, invece di provare (o classificare) tutti i dispositivi accoppiati.
 * Solo l'elenco gia' in cache dal sistema, nessuna scansione.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WearDevicePicker(current: String, onPick: (BondedDevice?) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var devices by remember { mutableStateOf(emptyList<BondedDevice>()) }
    LaunchedEffect(Unit) { devices = WearBridge.bondedDevices(context) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        LazyColumn(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                RowItem(
                    title = "Rilevamento automatico",
                    subtitle = "Prova prima i dispositivi di classe \"orologio\"" +
                        if (current.isBlank()) " · Attivo" else "",
                    onClick = { onPick(null); onDismiss() },
                )
            }
            items(devices) { d ->
                RowItem(
                    title = d.name,
                    subtitle = if (d.address == current) "Attivo" else null,
                    onClick = { onPick(d); onDismiss() },
                )
            }
            if (devices.isEmpty()) item {
                RowItem(title = "Nessun dispositivo Bluetooth accoppiato")
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

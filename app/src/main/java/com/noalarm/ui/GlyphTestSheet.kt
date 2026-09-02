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
import com.noalarm.data.Store
import com.noalarm.glyph.GlyphController

/**
 * Prova della Glyph Matrix. La SDK di Nothing non e' ispezionabile da qui:
 * questa schermata accende la matrice e riporta cosa risponde davvero il
 * dispositivo, cosi' si sceglie il canale giusto senza indovinare.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlyphTestSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val status by GlyphController.status.collectAsStateWithLifecycle()
    val settings by Store.settings.collectAsStateWithLifecycle()

    // La matrice non deve restare accesa quando il foglio si chiude.
    DisposableEffect(Unit) { onDispose { GlyphController.stop() } }

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
                "Guarda il retro del telefono mentre la prova e' in corso.",
                Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RowItem(
                title = "Libreria Glyph sul dispositivo",
                subtitle = when (status.available) {
                    null -> "Non ancora verificata"
                    true -> "Presente"
                    false -> "Assente: questo non e' un Nothing Phone (3)"
                },
            )
            RowItem("Servizio connesso", if (status.connected) "Si'" else "No")
            RowItem("register()", if (status.registered) "Riuscito" else "Non riuscito")
            RowItem(
                title = "Frame",
                subtitle = "${status.accepted} accettati · ${status.rejected} rifiutati " +
                    "su ${status.sent} inviati",
            )
            RowItem("Ultimo errore", status.lastError ?: "Nessuno")

            SectionLabel("Canale di scrittura")
            SwitchRow("Usa setAppMatrixFrame", settings.glyphAppChannel) { v ->
                Store.update { it.copy(glyphAppChannel = v) }
            }
            Text(
                "Se con un canale la matrice resta spenta, ferma la prova, cambia " +
                    "canale e riprova: e' l'unico modo per sapere quale dei due accetta " +
                    "i frame di un'app che non e' il Glyph Toy attivo.",
                Modifier.padding(horizontal = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (status.running) DotIconButton(
                    Icons.Outlined.Stop, "Ferma la prova",
                    { GlyphController.stop() },
                    size = 72,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                ) else DotIconButton(
                    Icons.Outlined.PlayArrow, "Avvia la prova",
                    { GlyphController.test(context) },
                    size = 72,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

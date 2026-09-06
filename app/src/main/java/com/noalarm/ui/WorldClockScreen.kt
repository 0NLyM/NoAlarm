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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.data.Store
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorldClockScreen() {
    val settings by Store.settings.collectAsStateWithLifecycle()
    val now = rememberNow(if (settings.showSeconds) 1000L else 15_000L)
    var picking by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                val here = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    DotText(
                        Format.clock(here, settings.use24h, settings.showSeconds),
                        // Piu' alta del testo: lascia respirare i numeri sfumati
                        // sopra e sotto quello attivo invece di tagliarli subito.
                        Modifier.fillMaxWidth().height(if (settings.showSeconds) 138.dp else 172.dp),
                        cell = if (settings.showSeconds) 8.dp else 12.dp,
                        color = MaterialTheme.colorScheme.onBackground,
                        accentChars = setOf(':'),
                        accentColor = MaterialTheme.colorScheme.secondary,
                        blinkAccent = true,
                        animateChanges = true,
                        groupMods = clockMods(settings.use24h, settings.showSeconds),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "${Format.dayLabel(here.dayOfWeek, false)} ${here.dayOfMonth} · ${ZoneId.systemDefault().id}".uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            items(settings.worldClocks, key = { it }) { zone ->
                val there = runCatching {
                    ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.of(zone))
                }.getOrNull()
                Box(Modifier.padding(horizontal = 16.dp)) {
                    Panel {
                        Box(Modifier.fillMaxWidth()) {
                            // Stesso impianto dell'orologio principale, identico per
                            // ogni citta': ora in dot-matrix e sotto nome e scarto.
                            Column(
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                DotText(
                                    there?.let { Format.clock(it, settings.use24h) } ?: "--:--",
                                    Modifier.fillMaxWidth().height(124.dp),
                                    cell = 8.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    accentChars = setOf(':'),
                                    accentColor = MaterialTheme.colorScheme.secondary,
                                    blinkAccent = true,
                                    animateChanges = true,
                                    groupMods = clockMods(settings.use24h, false),
                                )
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    Format.zoneCity(zone).uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${Format.zoneOffset(zone)} · ${Format.zoneRegion(zone)}".uppercase(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { Store.update { s -> s.copy(worldClocks = s.worldClocks - zone) } },
                                modifier = Modifier.align(Alignment.TopEnd).size(32.dp),
                            ) {
                                Icon(
                                    Icons.Outlined.Close,
                                    "Rimuovi ${Format.zoneCity(zone)}",
                                    Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { picking = true },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 104.dp),
            containerColor = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
        ) { Icon(Icons.Outlined.Add, "Aggiungi citta'") }
    }

    if (picking) {
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { picking = false },
            sheetState = sheet,
            containerColor = MaterialTheme.colorScheme.background,
        ) { ZonePicker { zone -> Store.update { s -> s.copy(worldClocks = (s.worldClocks + zone).distinct()) }; picking = false } }
    }
}

/**
 * Il giro di ogni gruppo di cifre dell'orologio: dopo le 23 tornano le 00,
 * dopo i 59 minuti gli 00. In 12h le ore vanno da 1 a 12 e non sono un
 * modulo pulito, quindi si lascia il conteggio libero a due cifre.
 */
private fun clockMods(use24h: Boolean, seconds: Boolean): List<Int> =
    listOf(if (use24h) 24 else 100, 60) + if (seconds) listOf(60) else emptyList()

@Composable
private fun ZonePicker(onPick: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    val all = remember {
        ZoneId.getAvailableZoneIds().filter { '/' in it && !it.startsWith("Etc/") }.sorted()
    }
    val shown = remember(query) {
        if (query.isBlank()) all.take(60)
        else all.filter { it.replace('_', ' ').contains(query, ignoreCase = true) }.take(60)
    }

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Cerca citta'") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            Modifier.fillMaxWidth().height(420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(shown, key = { it }) { zone ->
                RowItem(
                    title = Format.zoneCity(zone),
                    subtitle = "${Format.zoneRegion(zone)} · ${Format.zoneOffset(zone)}",
                    onClick = { onPick(zone) },
                )
            }
        }
    }
}

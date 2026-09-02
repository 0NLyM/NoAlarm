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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreTime
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.noalarm.clock.ClockService
import com.noalarm.data.Store
import com.noalarm.data.TimerItem

@Composable
fun TimerScreen() {
    val context = LocalContext.current
    val timers by Store.timers.collectAsStateWithLifecycle()
    val now = rememberNow(1000L)
    var digits by remember { mutableStateOf("") }
    var adding by remember { mutableStateOf(false) }

    if (timers.isEmpty() || adding) {
        Keypad(
            digits = digits,
            onDigit = { digits = (digits + it).trimStart('0').takeLast(6) },
            onBack = { digits = digits.dropLast(1) },
            onStart = {
                val ms = msOf(digits)
                if (ms > 0) {
                    val at = System.currentTimeMillis()
                    Store.putTimer(TimerItem(id = at, totalMs = ms, endAt = at + ms, remainingMs = ms, running = true))
                    ClockService.sync(context)
                    digits = ""
                    adding = false
                }
            },
            onCancel = if (timers.isEmpty()) null else { { digits = ""; adding = false } },
        )
        return
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp, 8.dp, 16.dp, 120.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(timers, key = { it.id }) { t -> TimerCard(t, now) }
            item {
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    DotIconButton(Icons.Outlined.Add, "Nuovo timer", { adding = true })
                }
            }
        }
    }
}

@Composable
private fun TimerCard(t: TimerItem, now: Long) {
    val context = LocalContext.current
    val left = t.remaining(now)
    Panel {
        Column(Modifier.fillMaxWidth()) {
            Text(
                if (t.expired) "SCADUTO" else t.label.ifBlank { Format.timer(t.totalMs) }.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = if (t.expired) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            DotText(
                Format.timer(left.coerceAtLeast(0)),
                Modifier.fillMaxWidth().height(64.dp),
                cell = 8.dp,
                color = MaterialTheme.colorScheme.onSurface,
                offColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { if (t.totalMs == 0L) 0f else (left.toFloat() / t.totalMs).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.secondary,
                trackColor = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DotIconButton(
                    Icons.Outlined.MoreTime, "Aggiungi un minuto",
                    { ClockService.timerAdd(context, t.id, 1) }, size = 56,
                )
                DotIconButton(
                    if (t.running) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                    if (t.running) "Metti in pausa" else "Avvia",
                    { ClockService.timerToggle(context, t.id) },
                    size = 56,
                    enabled = !t.expired,
                )
                DotIconButton(
                    Icons.Outlined.Delete, "Elimina il timer",
                    { Store.removeTimer(t.id); ClockService.sync(context) },
                    size = 56,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

private fun msOf(digits: String): Long {
    val d = digits.padStart(6, '0')
    val h = d.substring(0, 2).toLong()
    val m = d.substring(2, 4).toLong()
    val s = d.substring(4, 6).toLong()
    return ((h * 3600) + (m * 60) + s) * 1000
}

@Composable
private fun Keypad(
    digits: String,
    onDigit: (Char) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val d = digits.padStart(6, '0')
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        DotText(
            "${d.substring(0, 2)}:${d.substring(2, 4)}:${d.substring(4, 6)}",
            Modifier.fillMaxWidth().height(72.dp),
            cell = 7.dp,
            color = if (digits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onBackground,
            offColor = MaterialTheme.colorScheme.outline,
        )
        Text("ORE · MINUTI · SECONDI", style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)

        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.forEach { c -> DotButton(c.toString(), { onDigit(c) }) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DotButton("00", { onDigit('0'); onDigit('0') })
            DotButton("0", { onDigit('0') })
            DotIconButton(Icons.Outlined.Backspace, "Cancella l'ultima cifra", onBack, enabled = digits.isNotEmpty())
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onCancel != null) DotIconButton(Icons.Outlined.Close, "Annulla", onCancel, size = 64)
        DotIconButton(
            Icons.Outlined.PlayArrow,
            "Avvia il timer",
            onStart,
            size = 88,
            color = MaterialTheme.colorScheme.secondary,
            contentColor = MaterialTheme.colorScheme.onSecondary,
            enabled = digits.isNotEmpty() && msOf(digits) > 0,
        )
        }
    }
}

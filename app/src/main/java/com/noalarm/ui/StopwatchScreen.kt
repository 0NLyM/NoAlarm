package com.noalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.clock.ClockService
import com.noalarm.data.Store

@Composable
fun StopwatchScreen() {
    val context = LocalContext.current
    val sw by Store.stopwatch.collectAsStateWithLifecycle()
    val now = rememberNow(if (sw.running) 50L else 1000L)
    val elapsed = sw.elapsed(now)

    Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        DotText(
            Format.stopwatch(elapsed),
            Modifier.fillMaxWidth().height(80.dp),
            cell = 8.dp,
            color = MaterialTheme.colorScheme.onBackground,
            offColor = MaterialTheme.colorScheme.outline,
        )
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            DotButton(
                "Reset",
                { ClockService.stopwatchReset(context) },
                size = 64,
                enabled = elapsed > 0,
            )
            DotButton(
                if (sw.running) "Pausa" else if (elapsed > 0) "Riprendi" else "Avvia",
                { ClockService.stopwatchToggle(context) },
                size = 88,
                color = MaterialTheme.colorScheme.secondary,
                contentColor = MaterialTheme.colorScheme.onSecondary,
            )
            DotButton(
                "Giro",
                { ClockService.stopwatchLap(context) },
                size = 64,
                enabled = sw.running,
            )
        }
        Spacer(Modifier.height(24.dp))
        LazyColumn(
            Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        ) {
            itemsIndexed(sw.laps.reversed()) { i, lap ->
                val index = sw.laps.size - i
                val previous = if (index >= 2) sw.laps[index - 2] else 0L
                RowItem(
                    title = "GIRO %02d".format(index),
                    subtitle = "Totale ${Format.stopwatch(lap)}",
                    trailing = {
                        Text(Format.stopwatch(lap - previous), style = MaterialTheme.typography.bodyMedium)
                    },
                )
            }
        }
    }
}

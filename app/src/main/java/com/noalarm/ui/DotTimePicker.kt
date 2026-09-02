package com.noalarm.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.drop
import kotlin.math.abs

/** Giri virtuali della lista: abbastanza da far sembrare il rullo infinito. */
private const val LOOPS = 200
private val ITEM = 56.dp

/**
 * Selettore dell'orario in stile Nothing: due rulli di cifre dot-matrix.
 * La cifra al centro e' grande e piena, quelle sopra e sotto rimpiccioliscono
 * e sfumano nei punti spenti, come su un display a LED che scorre.
 */
@Composable
fun DotTimePicker(
    hour: Int,
    minute: Int,
    use24h: Boolean,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hours = remember(use24h) { if (use24h) (0..23).toList() else (1..12).toList() }
    val minutes = remember { (0..59).toList() }
    val pm = hour >= 12
    val shown = if (use24h) hour else if (hour % 12 == 0) 12 else hour % 12

    fun emit(h: Int, m: Int, afternoon: Boolean) =
        onChange(if (use24h) h else (h % 12) + if (afternoon) 12 else 0, m)

    Row(
        modifier.height(ITEM * 3),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DotWheel(hours, hours.indexOf(shown).coerceAtLeast(0), 72.dp) { emit(hours[it], minute, pm) }

        // I due punti lampeggiano al secondo, come l'orologio della schermata Sveglia.
        val blink = rememberNow(1000L) / 1000 % 2 == 0L
        Box(Modifier.width(26.dp).height(ITEM), contentAlignment = Alignment.Center) {
            DotText(
                ":",
                Modifier.size(18.dp, 38.dp),
                cell = 5.dp,
                color = if (blink) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.outline,
            )
        }

        DotWheel(minutes, minute.coerceIn(0, 59), 72.dp) { emit(shown, it, pm) }

        if (!use24h) {
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(false, true).forEach { afternoon ->
                    val on = afternoon == pm
                    DotButton(
                        label = if (afternoon) "PM" else "AM",
                        size = 44,
                        color = if (on) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (on) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { emit(shown, minute, afternoon) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DotWheel(
    values: List<Int>,
    selected: Int,
    width: Dp,
    onSelect: (Int) -> Unit,
) {
    // Si parte a meta' dei giri virtuali, cosi' si puo' scorrere in entrambi i sensi.
    val state = rememberLazyListState(values.size * (LOOPS / 2) + selected)
    val haptic = LocalHapticFeedback.current
    val itemPx = with(LocalDensity.current) { ITEM.toPx() }

    // Posizione continua della riga centrale: sfuma le cifre vicine mentre scorre.
    val center by remember(itemPx) {
        derivedStateOf {
            state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / itemPx
        }
    }

    LaunchedEffect(state, values.size) {
        // drop(1): la prima emissione e' lo stato iniziale, non una scelta dell'utente.
        snapshotFlow { state.firstVisibleItemIndex }.drop(1).collect {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onSelect(it.mod(values.size))
        }
    }

    LazyColumn(
        Modifier.width(width).height(ITEM * 3),
        state = state,
        contentPadding = PaddingValues(vertical = ITEM),
        flingBehavior = rememberSnapFlingBehavior(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(values.size * LOOPS) { index ->
            val d = abs(index - center).coerceIn(0f, 1f)   // 0 al centro, 1 ai bordi
            Box(Modifier.height(ITEM).fillMaxWidth(), contentAlignment = Alignment.Center) {
                DotText(
                    "%02d".format(values[index.mod(values.size)]),
                    Modifier.fillMaxWidth().height(ITEM - 14.dp),
                    cell = (7f - 2f * d).dp,
                    color = lerp(
                        MaterialTheme.colorScheme.onBackground,
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        d,
                    ),
                    offColor = if (d < 0.5f) MaterialTheme.colorScheme.outline else null,
                )
            }
        }
    }
}

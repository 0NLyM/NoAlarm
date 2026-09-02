package com.noalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noalarm.ui.theme.LocalDotOff
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

// Geometria della griglia, in celle di punti.
private const val DIGIT_H = DotFont.H          // 7 righe per cifra
private const val ITEM_H = DIGIT_H + 4         // passo fra due valori del rullo
private const val ROWS = 19                    // righe visibili nella finestra
private const val PAIR_W = 11                  // due cifre: 5 + 1 + 5
private val CELL = 8.dp

/**
 * Selettore dell'orario in stile Nothing.
 *
 * La griglia di punti spenti e' **fissa**, come i LED di un display vero: quello
 * che scorre sono solo i punti accesi, che si spostano di una cella alla volta
 * mentre trascini. Le cifre lontane dal centro sbiadiscono invece di rimpicciolire,
 * cosi' l'allineamento della griglia non si rompe mai.
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

    val hourPos = rememberWheel(hours.indexOf(shown).coerceAtLeast(0), hours.size) {
        emit(hours[it], minute, pm)
    }
    val minutePos = rememberWheel(minute.coerceIn(0, 59), minutes.size) { emit(shown, it, pm) }

    val on = MaterialTheme.colorScheme.onBackground
    val off = LocalDotOff.current
    val accent = MaterialTheme.colorScheme.secondary
    val blink = rememberNow(1000L) / 1000 % 2 == 0L
    val cellPx = with(LocalDensity.current) { CELL.toPx() }
    val itemPx = cellPx * ITEM_H

    // ore 0..10 | 3 vuote | due punti 14..18 | 3 vuote | minuti 22..32
    val colonCol = PAIR_W + 3
    val minuteCol = colonCol + DotFont.W + 3
    val cols = minuteCol + PAIR_W
    val width = CELL * cols
    val height = CELL * ROWS

    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width, height)) {
            Canvas(Modifier.size(width, height)) {
                val p = cellPx
                val r = p * 0.34f
                // La griglia spenta: sempre la stessa, non si muove mai.
                for (y in 0 until ROWS) for (x in 0 until cols) {
                    drawCircle(off, r, Offset(x * p + p / 2, y * p + p / 2))
                }
                wheel(hours, hourPos.value, 0, p, r, on)
                wheel(minutes, minutePos.value, minuteCol, p, r, on)
                // I due punti restano fermi al centro e lampeggiano al secondo.
                if (blink) {
                    val colon = DotFont.render(":", 0)
                    val top = (ROWS - DIGIT_H) / 2
                    for (y in 0 until DIGIT_H) for (x in 0 until DotFont.W) {
                        if (colon[y][x]) drawCircle(
                            accent, r,
                            Offset((colonCol + x) * p + p / 2, (top + y) * p + p / 2),
                        )
                    }
                }
            }

            // Due zone di trascinamento invisibili, una per rullo.
            Row(Modifier.fillMaxHeight()) {
                DragArea(CELL * PAIR_W, itemPx, hourPos)
                Spacer(Modifier.width(CELL * (minuteCol - PAIR_W)))
                DragArea(CELL * PAIR_W, itemPx, minutePos)
            }
        }

        if (!use24h) {
            Spacer(Modifier.width(12.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(false, true).forEach { afternoon ->
                    val active = afternoon == pm
                    DotButton(
                        label = if (afternoon) "PM" else "AM",
                        size = 44,
                        color = if (active) accent else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = if (active) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = { emit(shown, minute, afternoon) },
                    )
                }
            }
        }
    }
}

/** Posizione continua di un rullo, in unita' di "valore". */
@Composable
private fun rememberWheel(
    initial: Int,
    size: Int,
    onSelect: (Int) -> Unit,
): Animatable<Float, AnimationVector1D> {
    val pos = remember { Animatable(initial.toFloat()) }
    val haptic = LocalHapticFeedback.current
    val settled = remember { mutableStateOf(false) }
    val index = pos.value.roundToInt().mod(size)
    LaunchedEffect(index) {
        // Il primo passaggio e' lo stato iniziale, non una scelta: niente vibrazione.
        if (settled.value) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        settled.value = true
        onSelect(index)
    }
    return pos
}

@Composable
private fun DragArea(width: Dp, itemPx: Float, pos: Animatable<Float, AnimationVector1D>) {
    val scope = rememberCoroutineScope()
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .draggable(
                orientation = Orientation.Vertical,
                state = rememberDraggableState { delta ->
                    scope.launch { pos.snapTo(pos.value - delta / itemPx) }
                },
                onDragStopped = { velocity ->
                    // Un filo di inerzia, poi si aggancia al valore piu' vicino.
                    val target = (pos.value - velocity / itemPx * 0.10f).roundToInt().toFloat()
                    pos.animateTo(target, spring(dampingRatio = 0.8f, stiffness = 400f))
                },
            )
    )
}

/**
 * Accende i punti delle cifre visibili. Le righe sono arrotondate alla cella:
 * i LED si accendono e si spengono, non scivolano fra una posizione e l'altra.
 */
private fun DrawScope.wheel(
    values: List<Int>,
    pos: Float,
    colOffset: Int,
    p: Float,
    r: Float,
    on: Color,
) {
    val top = (ROWS - DIGIT_H) / 2f
    val from = floor(pos).toInt() - 1
    for (k in from..from + 3) {
        val distance = abs(k - pos)
        if (distance > 1.7f) continue
        val alpha = 1f - 0.74f * distance.coerceAtMost(1f)
        val grid = DotFont.render("%02d".format(values[k.mod(values.size)]), 1)
        val yTop = (top + (k - pos) * ITEM_H).roundToInt()
        for (y in 0 until DIGIT_H) {
            val row = yTop + y
            if (row < 0 || row >= ROWS) continue
            for (x in grid[y].indices) {
                if (!grid[y][x]) continue
                drawCircle(
                    on.copy(alpha = alpha), r,
                    Offset((colOffset + x) * p + p / 2, row * p + p / 2),
                )
            }
        }
    }
}

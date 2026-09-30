package com.noalarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Manopola circolare: ruotando col dito si sceglie un valore da una lista
 * discreta, distribuita in cerchio per indice (non per grandezza) - cosi'
 * passi non uniformi (es. 1..10 poi 15..60 di 5 in 5) restano ugualmente
 * spaziati sul quadrante, come su una manopola vera.
 */
@Composable
fun Knob(
    values: List<Int>,
    selected: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    diameter: Dp = 200.dp,
    content: @Composable () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val step = 360f / values.size
    val on = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.secondary
    val track = MaterialTheme.colorScheme.surfaceContainerHigh
    val index = values.indexOf(selected).let { if (it >= 0) it else nearestIndex(values, selected) }

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(diameter)
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(values) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val v = change.position - center
                            val angle = ((Math.toDegrees(atan2(v.x, -v.y).toDouble()) + 360) % 360).toFloat()
                            val i = (angle / step).roundToInt().mod(values.size)
                            if (values[i] != selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onChange(values[i])
                            }
                        }
                    }
                )
        ) {
            val radius = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            drawCircle(track, radius - 4.dp.toPx(), center, style = Stroke(4.dp.toPx()))
            values.forEachIndexed { i, _ ->
                val a = Math.toRadians((i * step).toDouble())
                val tickR = radius - 18.dp.toPx()
                val p = Offset(
                    center.x + (tickR * sin(a)).toFloat(),
                    center.y - (tickR * cos(a)).toFloat(),
                )
                drawCircle(
                    if (i == index) accent else on.copy(alpha = 0.35f),
                    if (i == index) 6.dp.toPx() else 3.dp.toPx(),
                    p,
                )
            }
            val needleAngle = Math.toRadians((index * step).toDouble())
            val needleR = radius - 34.dp.toPx()
            drawLine(
                accent,
                center,
                Offset(
                    center.x + (needleR * sin(needleAngle)).toFloat(),
                    center.y - (needleR * cos(needleAngle)).toFloat(),
                ),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        content()
    }
}

private fun nearestIndex(values: List<Int>, target: Int): Int =
    values.indices.minBy { abs(values[it] - target) }

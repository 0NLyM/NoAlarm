package com.noalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Manopola circolare: numeri piccoli fissi intorno al bordo (toccabili uno a
 * uno) con davanti la loro tacca a pillola - quella selezionata piu' lunga e
 * in accento - e al centro un disco che ruota per davvero, con un piccolo
 * indicatore sul bordo invece di una lancetta che arriva al centro (altrimenti
 * sembra un orologio, non una manopola). Trascinando il disco l'angolo segue
 * il dito in tempo reale; lasciandolo, o toccando un numero, si anima con una
 * piccola molla fino alla tacca piu' vicina invece di scattare di colpo.
 *
 * I valori sono distribuiti in cerchio per indice, non per grandezza: cosi'
 * un passo non uniforme (es. 1..10 poi 15..60 di 5 in 5) resta ugualmente
 * spaziato sul quadrante, come su una manopola vera.
 */
@Composable
fun Knob(
    values: List<Int>,
    selected: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    diameter: Dp = 220.dp,
    content: @Composable () -> Unit = {},
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val step = 360f / values.size
    val index = values.indexOf(selected).let { if (it >= 0) it else nearestIndex(values, selected) }

    val angle = remember { Animatable(index * step) }
    var dragging by remember { mutableStateOf(false) }
    var liveAngle by remember { mutableStateOf(index * step) }
    // true appena finito un trascinamento: dice all'effetto sotto di
    // ripartire dall'angolo lasciato dal dito invece che da quello vecchio
    // dell'Animatable - un solo effetto invece di due separati sullo stesso
    // Animatable, che altrimenti potrebbero competere fra loro (l'ultimo
    // vince, l'altro viene cancellato a meta').
    var justDragged by remember { mutableStateOf(false) }

    // Si anima verso la tacca scelta - dal drag appena lasciato, da un tocco
    // su un numero, o da un cambio esterno (es. i limiti Minimo/Massimo
    // spostano la selezione) - solo quando non si sta trascinando: durante
    // il drag l'angolo segue il dito, non la molla.
    LaunchedEffect(index, dragging) {
        if (dragging) return@LaunchedEffect
        if (justDragged) {
            angle.snapTo(liveAngle)
            justDragged = false
        }
        val target = index * step
        // Percorso piu' breve, non sempre in avanti: es. da 355 a 5 gradi sono
        // 10 gradi, non quasi un giro intero.
        val delta = ((target - angle.value + 540) % 360) - 180
        angle.animateTo(angle.value + delta, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow))
    }

    fun select(i: Int) {
        if (values[i] != selected) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onChange(values[i])
        }
    }

    val onSurface = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.secondary
    val knobColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    // Il quadrante con le tacche resta piu' piccolo del totale, per lasciare
    // spazio ai numeri tutt'intorno senza farli sovrapporre alle tacche.
    val dialDiameter = diameter - 48.dp

    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .size(dialDiameter)
                .then(
                    if (!enabled) Modifier else Modifier.pointerInput(values) {
                        detectDragGestures(
                            onDragStart = { dragging = true },
                            onDragEnd = { dragging = false; justDragged = true },
                            onDragCancel = { dragging = false; justDragged = true },
                        ) { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val v = change.position - center
                            val raw = ((Math.toDegrees(atan2(v.x, -v.y).toDouble()) + 360) % 360).toFloat()
                            liveAngle = raw
                            select((raw / step).roundToInt().mod(values.size))
                        }
                    }
                )
        ) {
            val r = size.minDimension / 2f
            val center = Offset(size.width / 2f, size.height / 2f)
            val displayAngle = if (dragging) liveAngle else angle.value

            // Solco circolare: da' l'idea di un incavo dove girano le tacche,
            // invece di un semplice sfondo piatto dietro di loro.
            drawCircle(trackColor, r - 5.dp.toPx(), center, style = Stroke(9.dp.toPx()))

            // Tacche a pillola, fisse intorno al bordo: quella selezionata
            // piu' lunga e in accento, le altre corte e smorzate.
            values.forEachIndexed { i, _ ->
                val isSelected = i == index
                val length = (if (isSelected) 18.dp else 10.dp).toPx()
                val width = (if (isSelected) 5.dp else 3.dp).toPx()
                rotate(i * step, pivot = center) {
                    drawRoundRect(
                        color = if (isSelected) accent else onSurface.copy(alpha = 0.3f),
                        topLeft = Offset(center.x - width / 2f, center.y - r + 2.dp.toPx()),
                        size = Size(width, length),
                        cornerRadius = CornerRadius(width / 2f),
                    )
                }
            }

            // Disco centrale che ruota per davvero: un piccolo indicatore a
            // pillola vicino al suo bordo, non una lancetta fino al centro.
            val knobRadius = r - 40.dp.toPx()
            drawCircle(knobColor, knobRadius, center)
            rotate(displayAngle, pivot = center) {
                drawRoundRect(
                    color = accent,
                    topLeft = Offset(center.x - 3.dp.toPx(), center.y - knobRadius + 6.dp.toPx()),
                    size = Size(6.dp.toPx(), 14.dp.toPx()),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
            }
        }

        // Numeri intorno al bordo, sempre visibili e toccabili uno a uno.
        val labelRadiusPx = with(density) { (diameter / 2 - 8.dp).toPx() }
        values.forEachIndexed { i, v ->
            val a = Math.toRadians((i * step).toDouble())
            val x = (labelRadiusPx * sin(a)).toFloat().roundToInt()
            val y = (-labelRadiusPx * cos(a)).toFloat().roundToInt()
            Text(
                v.toString(),
                modifier = Modifier
                    .offset { IntOffset(x, y) }
                    .width(22.dp)
                    .clickable(enabled = enabled) { select(i) },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = if (i == index) accent else onSurface.copy(alpha = 0.5f),
            )
        }

        content()
    }
}

private fun nearestIndex(values: List<Int>, target: Int): Int =
    values.indices.minBy { abs(values[it] - target) }

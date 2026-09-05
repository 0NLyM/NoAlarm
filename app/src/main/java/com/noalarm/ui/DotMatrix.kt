package com.noalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import com.noalarm.ui.theme.LocalDotOff
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.floor

/**
 * Testo dot-matrix: la stessa griglia di punti della Glyph Matrix, disegnata su Canvas.
 * [cell] e' il passo della griglia; i punti spenti restano visibili in [offColor]
 * per dare l'effetto "display sempre acceso" tipico di Nothing.
 *
 * [accentChars] (tipicamente i ":") si disegnano in [accentColor] invece che in
 * [color]; se [blinkAccent] e' true lampeggiano al secondo, come sul selettore
 * dell'orario.
 *
 * [animateChanges]: ogni cifra che cambia valore gira come un rullo - la stessa
 * cifra vecchia esce da un lato mentre la nuova entra dall'altro, come il
 * carosello del selettore dell'ora - invece di saltare di colpo. Pensato per i
 * display che ticchettano da soli (cronometro, timer, orologio), non per quelli
 * che cambiano solo per un tocco dell'utente.
 */
@Composable
fun DotText(
    text: String,
    modifier: Modifier = Modifier,
    cell: Dp = 6.dp,
    color: Color = Color.White,
    offColor: Color? = LocalDotOff.current,
    tracking: Int = 1,
    accentChars: Set<Char> = emptySet(),
    accentColor: Color = color,
    blinkAccent: Boolean = false,
    animateChanges: Boolean = false,
) {
    val cols = DotFont.width(text, tracking)
    val blinkOn = if (blinkAccent) rememberNow(1000L) / 1000 % 2 == 0L else true
    val step = DotFont.W + tracking

    // Un rullo per cifra, come sul selettore dell'ora: quando una posizione
    // cambia valore scorre verso il prossimo invece di saltare di colpo. Non
    // ricreato a ogni ricomposizione, solo quando cambia la lunghezza del testo.
    val rolls = remember(text.length) {
        List(text.length) { i ->
            val d = text.getOrNull(i)?.takeIf { it in '0'..'9' }
            Animatable((d?.minus('0'))?.toFloat() ?: 0f)
        }
    }
    var previous by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (animateChanges && previous.length == text.length) {
            text.forEachIndexed { i, c ->
                val newDigit = if (c in '0'..'9') c - '0' else null
                val oldDigit = previous.getOrNull(i)?.let { p -> if (p in '0'..'9') p - '0' else null }
                if (newDigit != null && oldDigit != null && newDigit != oldDigit) {
                    // la via piu' breve sul cerchio 0..9, come le griglie del selettore.
                    var delta = (newDigit - oldDigit).toFloat()
                    if (delta > 5) delta -= 10
                    if (delta < -5) delta += 10
                    val target = rolls[i].value + delta
                    launch { rolls[i].animateTo(target, tween(380, easing = FastOutSlowInEasing)) }
                }
            }
        }
        previous = text
    }

    Canvas(modifier) {
        val p = cell.toPx()
        val r = p * 0.36f
        val ox = (size.width - cols * p) / 2f + p / 2f
        val oy = (size.height - DotFont.H * p) / 2f + p / 2f

        for (i in text.indices) {
            val ch = text[i]
            val colX = ox + i * step * p
            val isAccent = ch in accentChars
            val rollValue = if (animateChanges && i < rolls.size && ch in '0'..'9') rolls[i].value else null

            if (rollValue != null) {
                // Sfondo spento fisso: i led sempre accesi non scorrono, solo la cifra.
                if (offColor != null) {
                    for (y in 0 until DotFont.H) for (x in 0 until DotFont.W) {
                        drawCircle(offColor, r, Offset(colX + x * p, oy + y * p))
                    }
                }
                val base = floor(rollValue).toInt()
                val frac = rollValue - base
                val outDigit = '0' + Math.floorMod(base, 10)
                drawRollingDigit(outDigit, colX, oy - frac * DotFont.H * p, p, r, color, 1f - frac)
                if (frac > 0.001f) {
                    val inDigit = '0' + Math.floorMod(base + 1, 10)
                    drawRollingDigit(inDigit, colX, oy + (1f - frac) * DotFont.H * p, p, r, color, frac)
                }
            } else {
                for (y in 0 until DotFont.H) for (x in 0 until DotFont.W) {
                    val lit = DotFont.on(ch, x, y) && !(isAccent && !blinkOn)
                    val c = if (lit) (if (isAccent) accentColor else color) else offColor ?: continue
                    drawCircle(c, r, Offset(colX + x * p, oy + y * p))
                }
            }
        }
    }
}

private fun DrawScope.drawRollingDigit(
    ch: Char,
    colX: Float,
    rowY: Float,
    p: Float,
    r: Float,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.01f) return
    val c = color.copy(alpha = color.alpha * alpha)
    for (y in 0 until DotFont.H) for (x in 0 until DotFont.W) {
        if (!DotFont.on(ch, x, y)) continue
        drawCircle(c, r, Offset(colX + x * p, rowY + y * p))
    }
}

/** Dimensione naturale di [text] alla griglia [cell]: (larghezza, altezza). */
fun dotSize(text: String, cell: Dp, tracking: Int = 1): Pair<Dp, Dp> =
    cell * DotFont.width(text, tracking) to cell * DotFont.H

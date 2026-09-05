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
import androidx.compose.ui.unit.Dp
import com.noalarm.ui.theme.LocalDotOff
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Testo dot-matrix: la stessa griglia di punti della Glyph Matrix, disegnata su Canvas.
 * [cell] e' il passo della griglia; i punti spenti restano visibili in [offColor]
 * per dare l'effetto "display sempre acceso" tipico di Nothing.
 *
 * [accentChars] (tipicamente i ":") si disegnano in [accentColor] invece che in
 * [color]; se [blinkAccent] e' true lampeggiano al secondo, come sul selettore
 * dell'orario.
 *
 * [animateChanges]: ogni cifra che cambia valore fa un piccolo battito invece di
 * un taglio secco - la stessa sensazione del carosello a griglia. Pensato per i
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
    val grid = DotFont.render(text, tracking)
    val cols = if (grid.isEmpty()) 0 else grid[0].size
    val blinkOn = if (blinkAccent) rememberNow(1000L) / 1000 % 2 == 0L else true
    val step = DotFont.W + tracking

    // Un Animatable per carattere: quando il valore in quella posizione cambia,
    // riparte da un po' sbiadito e piccolo e torna pieno - non ricreato a ogni
    // ricomposizione, solo quando cambia la lunghezza del testo.
    val pulses = remember(text.length) { List(text.length) { Animatable(1f) } }
    var previous by remember { mutableStateOf(text) }
    LaunchedEffect(text) {
        if (animateChanges && previous.length == text.length) {
            text.forEachIndexed { i, c ->
                if (previous.getOrNull(i) != c) {
                    launch {
                        pulses[i].snapTo(0.55f)
                        pulses[i].animateTo(1f, tween(140, easing = FastOutSlowInEasing))
                    }
                }
            }
        }
        previous = text
    }

    Canvas(modifier) {
        val p = cell.toPx()
        val r = p * 0.36f
        // Centra la griglia nello spazio disponibile.
        val ox = (size.width - cols * p) / 2f + p / 2f
        val oy = (size.height - DotFont.H * p) / 2f + p / 2f
        for (y in 0 until DotFont.H) for (x in 0 until cols) {
            val charIndex = x / step
            val isAccent = charIndex < text.length && text[charIndex] in accentChars
            val lit = grid[y][x] && !(isAccent && !blinkOn)
            val c = if (lit) (if (isAccent) accentColor else color) else offColor ?: continue
            val pulse = if (animateChanges && charIndex < pulses.size) pulses[charIndex].value else 1f
            val faded = c.copy(alpha = c.alpha * pulse.coerceIn(0.55f, 1f))
            drawCircle(faded, r * (0.7f + 0.3f * pulse), Offset(ox + x * p, oy + y * p))
        }
    }
}

/** Dimensione naturale di [text] alla griglia [cell]: (larghezza, altezza). */
fun dotSize(text: String, cell: Dp, tracking: Int = 1): Pair<Dp, Dp> =
    cell * DotFont.width(text, tracking) to cell * DotFont.H

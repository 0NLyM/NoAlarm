package com.noalarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Testo dot-matrix: la stessa griglia di punti della Glyph Matrix, disegnata su Canvas.
 * [cell] e' il passo della griglia; i punti spenti restano visibili in [offColor]
 * per dare l'effetto "display sempre acceso" tipico di Nothing.
 */
@Composable
fun DotText(
    text: String,
    modifier: Modifier = Modifier,
    cell: Dp = 6.dp,
    color: Color = Color.White,
    offColor: Color? = null,
    tracking: Int = 1,
) {
    val grid = DotFont.render(text, tracking)
    val cols = if (grid.isEmpty()) 0 else grid[0].size
    Canvas(modifier) {
        val p = cell.toPx()
        val r = p * 0.36f
        // Centra la griglia nello spazio disponibile.
        val ox = (size.width - cols * p) / 2f + p / 2f
        val oy = (size.height - DotFont.H * p) / 2f + p / 2f
        for (y in 0 until DotFont.H) for (x in 0 until cols) {
            val c = if (grid[y][x]) color else offColor ?: continue
            drawCircle(c, r, Offset(ox + x * p, oy + y * p))
        }
    }
}

/** Dimensione naturale di [text] alla griglia [cell]: (larghezza, altezza). */
fun dotSize(text: String, cell: Dp, tracking: Int = 1): Pair<Dp, Dp> =
    cell * DotFont.width(text, tracking) to cell * DotFont.H

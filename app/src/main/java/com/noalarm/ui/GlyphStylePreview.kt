package com.noalarm.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import com.noalarm.glyph.GlyphRenderer
import com.noalarm.glyph.Matrix

/**
 * Anteprima animata di come [style] appare sulla Glyph Matrix: usa lo stesso
 * [GlyphRenderer] che disegna l'hardware vero, quindi cio' che si vede qui e'
 * esattamente cio' che suonera' sul retro del telefono - non serve un Nothing
 * Phone per vederlo, e' puro rendering di pixel, nessuna chiamata alla SDK.
 */
@Composable
fun GlyphStylePreview(
    style: GlyphStyle,
    label: String,
    modifier: Modifier = Modifier,
    cell: Dp = 3.dp,
) {
    val settings by Store.settings.collectAsStateWithLifecycle()
    // 12 fps come sulla matrice vera, cosi' l'anteprima ha lo stesso ritmo.
    val elapsedMs = rememberNow(1000L / GlyphRenderer.FPS)
    val matrix = remember(style) { Matrix() }
    // I colori sono @Composable: vanno letti qui, non dentro la lambda di
    // disegno di Canvas, che gira con un DrawScope come receiver.
    val background = MaterialTheme.colorScheme.background
    val off = MaterialTheme.colorScheme.onSurfaceVariant
    val on = MaterialTheme.colorScheme.onBackground

    Canvas(modifier.background(background)) {
        val frame = (elapsedMs / (1000L / GlyphRenderer.FPS)).toInt()
        GlyphRenderer.ringing(matrix, style, label.uppercase(), settings.use24h, frame)
        val p = cell.toPx()
        val r = p * 0.36f
        for (y in 0 until Matrix.SIZE) for (x in 0 until Matrix.SIZE) {
            val level = matrix.pixels[y * Matrix.SIZE + x]
            drawCircle(
                if (level > 0) on.copy(alpha = (level / 255f).coerceIn(0.25f, 1f)) else off,
                r,
                Offset(x * p + p / 2, y * p + p / 2),
            )
        }
    }
}

/** Scheda selezionabile con l'anteprima sopra il nome dello stile. */
@Composable
fun GlyphStyleCard(
    style: GlyphStyle,
    label: String,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) = Column(
    modifier
        .clip(RoundedCornerShape(4.dp))
        .background(
            if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
            else MaterialTheme.colorScheme.surfaceContainer
        )
        .border(
            1.dp,
            if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline,
            RoundedCornerShape(4.dp),
        )
        .clickable(onClick = onClick)
        .padding(8.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
) {
    GlyphStylePreview(style, label, Modifier.size((Matrix.SIZE * 3).dp))
    Spacer(Modifier.height(6.dp))
    Text(
        name,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) MaterialTheme.colorScheme.secondary
        else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

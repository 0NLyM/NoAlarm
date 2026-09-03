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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import com.noalarm.glyph.GlyphRenderer
import com.noalarm.glyph.Matrix
import kotlin.math.sqrt

/**
 * Anteprima animata di come [style] appare sulla Glyph Matrix: usa lo stesso
 * [GlyphRenderer] che disegna l'hardware vero, quindi cio' che si vede qui e'
 * esattamente cio' che suonera' sul retro del telefono - non serve un Nothing
 * Phone per vederlo, e' puro rendering di pixel, nessuna chiamata alla SDK.
 *
 * Lo sfondo di punti spenti e' ritagliato in un cerchio, come la matrice fisica
 * del Phone (3): fuori da quel cerchio non si disegna nulla, cosi' la forma
 * resta quella vera invece di un quadrato pieno di puntini.
 */
@Composable
fun GlyphStylePreview(
    style: GlyphStyle,
    label: String,
    modifier: Modifier = Modifier,
    cell: Dp = 3.dp,
    onColor: Color = MaterialTheme.colorScheme.onBackground,
    offColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.background,
) {
    val settings by Store.settings.collectAsStateWithLifecycle()
    // 12 fps come sulla matrice vera, cosi' l'anteprima ha lo stesso ritmo.
    val elapsedMs = rememberNow(1000L / GlyphRenderer.FPS)
    val matrix = remember(style) { Matrix() }

    Canvas(modifier.background(backgroundColor)) {
        val frame = (elapsedMs / (1000L / GlyphRenderer.FPS)).toInt()
        GlyphRenderer.ringing(matrix, style, label.uppercase(), settings.use24h, frame)
        val p = cell.toPx()
        val r = p * 0.36f
        val center = (Matrix.SIZE - 1) / 2f
        val radius = Matrix.SIZE / 2f
        for (y in 0 until Matrix.SIZE) for (x in 0 until Matrix.SIZE) {
            val dx = x - center
            val dy = y - center
            if (sqrt(dx * dx + dy * dy) > radius) continue
            val level = matrix.pixels[y * Matrix.SIZE + x]
            drawCircle(
                if (level > 0) onColor.copy(alpha = (level / 255f).coerceIn(0.25f, 1f)) else offColor,
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

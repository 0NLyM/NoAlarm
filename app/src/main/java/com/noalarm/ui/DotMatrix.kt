package com.noalarm.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.noalarm.ui.theme.LocalDotOff
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign

/** Quanto lontano (in numeri) resta visibile un vicino del rullo. */
private const val ROLL_MAX_DIST = 2.2f

/** Distanza fra un numero e il successivo, in altezze di cifra. */
private const val ROLL_GAP = 0.75f

/** Quanto rimpicciolisce un numero per ogni passo di distanza: e' la profondita'. */
private const val ROLL_DEPTH = 0.42f

/** Quanto la fascia a piena intensita' sconfina verso i vicini, prima di sfumare. */
private const val ROLL_FADE = 0.25f

/**
 * Sotto questo intervallo fra due cambi il gruppo salta invece di scorrere:
 * i centesimi del cronometro cambiano ogni 50ms, scorrerebbero troppo in
 * fretta per leggerli e renderebbero confusa anche l'animazione dei secondi.
 * Il tempo mostrato resta comunque quello esatto, si salta solo l'animazione.
 */
private const val ROLL_FAST_MS = 200L

/** Un gruppo di cifre consecutive (le ore, i minuti, ...) che scorre insieme. */
private class DigitGroup(val start: Int, val len: Int, val mod: Int)

private fun pow10(n: Int): Int {
    var v = 1
    repeat(n) { v *= 10 }
    return v
}

private fun groupsOf(text: String, mods: List<Int>): List<DigitGroup> {
    val out = mutableListOf<DigitGroup>()
    var i = 0
    while (i < text.length) {
        if (text[i] !in '0'..'9') { i++; continue }
        var j = i
        while (j < text.length && text[j] in '0'..'9') j++
        val len = j - i
        // Il modulo non puo' superare le cifre del gruppo, altrimenti un
        // vicino a due cifre in una colonna sola sborderebbe sul separatore.
        val mod = minOf(mods.getOrNull(out.size) ?: pow10(len), pow10(len))
        out += DigitGroup(i, len, mod)
        i = j
    }
    return out
}

private fun valueOf(text: String, g: DigitGroup): Int =
    text.substring(g.start, g.start + g.len).toIntOrNull() ?: 0

/** Il punto piu' vicino a [current] che rappresenta [value] sul cerchio [mod]. */
private fun nearestCongruent(current: Float, value: Int, mod: Int): Float =
    value + ((current - value) / mod).roundToInt() * mod.toFloat()

/**
 * Testo dot-matrix: la stessa griglia di punti della Glyph Matrix, disegnata su Canvas.
 * [cell] e' il passo della griglia; i punti spenti restano visibili in [offColor]
 * per dare l'effetto "display sempre acceso" tipico di Nothing.
 *
 * [accentChars] (tipicamente i ":") si disegnano in [accentColor] invece che in
 * [color]; se [blinkAccent] e' true lampeggiano al secondo.
 *
 * [animateChanges]: le cifre scorrono come un contachilometri, un gruppo alla
 * volta (ore, minuti e secondi si muovono a coppia, non cifra per cifra), con
 * un accenno sfumato del numero prima e dopo. Pensato per i display che
 * ticchettano da soli (cronometro, timer, orologio).
 *
 * [groupMods] e' il modulo di ogni gruppo da sinistra - 24, 60, 60 per un
 * orologio - e serve solo a sapere quale numero mostrare sfumato sopra e sotto
 * quello attivo. Senza, si assume 10^cifre.
 *
 * [forceRoll] fa scorrere anche i cambi che di norma saltano - quelli troppo
 * rapidi da leggere e i salti lunghi. Serve a chi sa che il prossimo cambio e'
 * voluto e non un ticchettio: un azzeramento, o la sveglia in programma che
 * cambia.
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
    groupMods: List<Int> = emptyList(),
    forceRoll: Boolean = false,
) {
    val cols = DotFont.width(text, tracking)
    val blinkOn = if (blinkAccent) rememberNow(1000L) / 1000 % 2 == 0L else true
    val step = DotFont.W + tracking

    // La "forma" del testo (cifre sostituite da #) cambia solo se cambia il
    // formato: finche' resta uguale i rulli mantengono la loro posizione.
    val shape = remember(text) { text.map { if (it in '0'..'9') '#' else it }.joinToString("") }
    val groups = remember(shape, groupMods) { groupsOf(text, groupMods) }
    val rolls = remember(shape, groupMods) { groups.map { Animatable(valueOf(text, it).toFloat()) } }
    val lastChange = remember(shape, groupMods) { LongArray(groups.size) }
    var previous by remember(shape) { mutableStateOf(text) }

    // Le animazioni vivono nello scope del composable, non in quello dell'effetto:
    // il testo cambia a ogni tick (50ms sul cronometro) e un LaunchedEffect(text)
    // le avrebbe interrotte tutte a meta' a ogni tick, lasciando ferme le cifre
    // piu' lente proprio mentre stavano scorrendo.
    val scope = rememberCoroutineScope()
    LaunchedEffect(text, animateChanges) {
        if (animateChanges && previous.length == text.length) {
            val now = System.currentTimeMillis()
            groups.forEachIndexed { i, g ->
                val to = valueOf(text, g)
                if (to == valueOf(previous, g)) return@forEachIndexed
                // Bersaglio assoluto invece che incrementale: anche se
                // l'animazione precedente viene interrotta a meta', il rullo
                // si ferma sempre esattamente su un numero, allineato agli altri.
                val target = nearestCongruent(rolls[i].value, to, g.mod)
                val jump = !forceRoll && (
                    abs(target - rolls[i].value) > 1.5f || now - lastChange[i] < ROLL_FAST_MS
                    )
                lastChange[i] = now
                scope.launch {
                    if (jump) {
                        rolls[i].snapTo(to.toFloat())
                    } else {
                        rolls[i].animateTo(target, spring(dampingRatio = 1f, stiffness = 260f))
                        // Riporta il valore dentro un giro: altrimenti dopo ore
                        // di secondi cresce senza limite e perde precisione.
                        rolls[i].snapTo(Math.floorMod(rolls[i].value.roundToInt(), g.mod).toFloat())
                    }
                }
            }
        }
        previous = text
    }

    // Il livello fuori schermo serve alla sfumatura dei bordi: senza, la
    // maschera cancellerebbe anche quello che c'e' sotto la finestra.
    val canvas = if (animateChanges) {
        modifier.clipToBounds().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    } else modifier

    Canvas(canvas) {
        val p = cell.toPx()
        val r = p * 0.36f
        val ox = (size.width - cols * p) / 2f + p / 2f
        val oy = (size.height - DotFont.H * p) / 2f + p / 2f
        val rolling = animateChanges && groups.size == rolls.size

        // Sfondo sempre acceso: la griglia di punti spenti non scorre mai.
        if (offColor != null) {
            for (i in text.indices) for (y in 0 until DotFont.H) for (x in 0 until DotFont.W) {
                drawCircle(offColor, r, Offset(ox + (i * step + x) * p, oy + y * p))
            }
        }

        for (i in text.indices) {
            val ch = text[i]
            // Le cifre dei gruppi le disegnano i rulli, qui restano i separatori.
            if (rolling && ch in '0'..'9') continue
            val isAccent = ch in accentChars
            if (isAccent && !blinkOn) continue
            val colX = ox + i * step * p
            val c = if (isAccent) accentColor else color
            for (y in 0 until DotFont.H) for (x in 0 until DotFont.W) {
                if (DotFont.on(ch, x, y)) drawCircle(c, r, Offset(colX + x * p, oy + y * p))
            }
        }

        if (!rolling) return@Canvas
        val cy = size.height / 2f
        val rowSpacing = DotFont.H * p * ROLL_GAP
        groups.forEachIndexed { gi, g ->
            val v = rolls[gi].value
            val base = floor(v).toInt()
            // Centro del gruppo: i numeri rimpiccioliti restano incolonnati qui.
            val localCenter = ((g.len - 1) * step + DotFont.W - 1) / 2f
            val groupCx = ox + (g.start * step + localCenter) * p
            for (d in base - 1..base + 2) {
                val rel = d - v
                val dist = abs(rel)
                if (dist > ROLL_MAX_DIST) continue
                val norm = (dist / ROLL_MAX_DIST).coerceAtMost(1f)
                val alpha = (1f - norm).pow(1.9f)
                if (alpha <= 0.02f) continue
                // Prospettiva: rimpicciolisce il numero intero - passo fra i punti
                // e raggio insieme - non solo i suoi punti, altrimenti resta
                // grande uguale e sembra soltanto piu' sottile. Le file lontane
                // si stringono un po' fra loro, come una ruota che si allontana.
                val scale = 1f / (1f + dist * ROLL_DEPTH)
                val pd = p * scale
                val yc = cy + sign(rel) * dist.pow(0.85f) * rowSpacing
                val s = Math.floorMod(d, g.mod).toString().padStart(g.len, '0')
                val c = color.copy(alpha = color.alpha * alpha)
                for (j in 0 until g.len) {
                    val glyph = s.getOrElse(j) { '0' }
                    for (yy in 0 until DotFont.H) for (xx in 0 until DotFont.W) {
                        if (!DotFont.on(glyph, xx, yy)) continue
                        val gx = groupCx + (j * step + xx - localCenter) * pd
                        val gy = yc + (yy - (DotFont.H - 1) / 2f) * pd
                        drawCircle(c, r * scale, Offset(gx, gy))
                    }
                }
            }
        }

        // I numeri di sfondo svaniscono verso i bordi invece di essere tagliati
        // di netto. La fascia piena copre il numero attivo e un pezzo di strada
        // verso i vicini, cosi' se ne vede una parte prima che sfumino.
        val bandHalf = DotFont.H * p / 2f + r + ROLL_FADE * rowSpacing
        val top = ((cy - bandHalf) / size.height).coerceIn(0f, 1f)
        val bottom = ((cy + bandHalf) / size.height).coerceIn(top, 1f)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Transparent,
                top to Color.Black,
                bottom to Color.Black,
                1f to Color.Transparent,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
}

/** Dimensione naturale di [text] alla griglia [cell]: (larghezza, altezza). */
fun dotSize(text: String, cell: Dp, tracking: Int = 1): Pair<Dp, Dp> =
    cell * DotFont.width(text, tracking) to cell * DotFont.H

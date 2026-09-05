package com.noalarm.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.math.roundToInt

// Taglia dei punti: quella "attiva" al centro e quella base dei vicini, in
// scala dp - le stesse proporzioni provate nell'anteprima.
private val BASE_CELL = 6.2.dp
private val ACTIVE_CELL = 8.2.dp
private val BOX_SIZE = 148.dp

private fun wrapModF(v: Float, mod: Float): Float {
    val r = v % mod
    return if (r < 0f) r + mod else r
}

private fun shortestDelta(from: Float, to: Float, mod: Int): Float {
    var d = (to - from) % mod
    if (d > mod / 2f) d -= mod
    if (d < -mod / 2f) d += mod
    return d
}

/**
 * Selettore dell'ora a griglia bidimensionale: su/giu' cambia la decina,
 * sinistra/destra l'unita', sferico (oltre l'ultima riga o colonna si
 * riparte dalla prima). Sostituisce DotTimePicker quando le ore sono in
 * formato 24h; DotTimePicker resta - non rimosso, solo non piu' chiamato
 * da qui - per il 12h, dove 1..12 non si presta alla stessa griglia
 * decina/unita'.
 */
@Composable
fun GridTimePicker(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onBackground
    val accent = MaterialTheme.colorScheme.secondary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.Top) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ORE", style = MaterialTheme.typography.labelMedium, color = labelColor)
            Spacer(Modifier.height(8.dp))
            GridField(
                rows = 3, cols = 10,
                valid = { r, c -> r * 10 + c <= 23 },
                value = hour,
                onChange = { onChange(it, minute) },
                color = ink,
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(30.dp)) {
            Text("00", style = MaterialTheme.typography.labelMedium, color = Color.Transparent)
            Spacer(Modifier.height(8.dp))
            DotColon(accent, Modifier.width(30.dp).height(BOX_SIZE))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MINUTI", style = MaterialTheme.typography.labelMedium, color = labelColor)
            Spacer(Modifier.height(8.dp))
            GridField(
                rows = 6, cols = 10,
                valid = { _, _ -> true },
                value = minute,
                onChange = { onChange(hour, it) },
                color = ink,
            )
        }
    }
}

@Composable
private fun DotColon(color: Color, modifier: Modifier = Modifier) {
    val blinkOn = rememberNow(1000L) / 1000 % 2 == 0L
    val alpha by animateFloatAsState(if (blinkOn) 1f else 0f, tween(260), label = "colon")
    Canvas(modifier) {
        if (alpha <= 0.02f) return@Canvas
        val cell = ACTIVE_CELL.toPx()
        val oy = size.height / 2f - (DotFont.H * cell) / 2f + cell / 2f
        val ox = size.width / 2f
        val r = cell * 0.4f
        val c = color.copy(alpha = alpha)
        for (y in 0 until DotFont.H) {
            if (!DotFont.on(':', 2, y)) continue
            drawCircle(c, r, Offset(ox, oy + y * cell))
        }
    }
}

@Composable
private fun GridField(
    rows: Int,
    cols: Int,
    valid: (Int, Int) -> Boolean,
    value: Int,
    onChange: (Int) -> Unit,
    color: Color,
) {
    val scope = rememberCoroutineScope()
    val dragR = remember { Animatable((value / 10).toFloat()) }
    val dragC = remember { Animatable((value % 10).toFloat()) }
    val press = remember { Animatable(1f) }
    var lastReported by remember { mutableIntStateOf(value) }
    val density = LocalDensity.current
    val boxPx = with(density) { BOX_SIZE.toPx() }
    val step = boxPx / 2.5f

    fun maxColOf(r: Int): Int {
        var c = cols - 1
        while (c > 0 && !valid(r, c)) c--
        return c
    }
    fun wrapPos(r: Float, c: Float): Pair<Int, Int> {
        val wr = Math.floorMod(r.roundToInt(), rows)
        val maxC = maxColOf(wr)
        val wc = Math.floorMod(c.roundToInt(), maxC + 1)
        return wr to wc
    }
    suspend fun snapTo(r: Float, c: Float) {
        val (wr, wc) = wrapPos(r, c)
        val targetR = dragR.value + shortestDelta(dragR.value, wr.toFloat(), rows)
        val targetC = dragC.value + shortestDelta(dragC.value, wc.toFloat(), cols)
        coroutineScope {
            launch { dragR.animateTo(targetR, spring(dampingRatio = 1.09f, stiffness = 190f)) }
            launch { dragC.animateTo(targetC, spring(dampingRatio = 1.09f, stiffness = 190f)) }
        }
        val newValue = wr * 10 + wc
        if (newValue != lastReported) {
            lastReported = newValue
            onChange(newValue)
        }
    }

    Box(
        Modifier
            .size(BOX_SIZE)
            .clip(RoundedCornerShape(20.dp))
            .pointerInput(rows, cols) {
                var totalDrag = Offset.Zero
                var velocity = Offset.Zero
                var lastPos = Offset.Zero
                detectDragGestures(
                    onDragStart = { offset ->
                        totalDrag = Offset.Zero
                        velocity = Offset.Zero
                        lastPos = offset
                        scope.launch { press.animateTo(0.86f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                    },
                    onDragEnd = {
                        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                        scope.launch {
                            if (hypot(totalDrag.x, totalDrag.y) < 8f) {
                                // tocco diretto: salta al numero toccato invece di trascinare.
                                val localX = lastPos.x - boxPx / 2f
                                val localY = lastPos.y - boxPx / 2f
                                snapTo(dragR.value + localY / step, dragC.value + localX / step)
                            } else {
                                // un filo di inerzia dal gesto, proiettando qualche fotogramma avanti.
                                snapTo(
                                    dragR.value + (velocity.y / step) * 6f,
                                    dragC.value + (velocity.x / step) * 6f,
                                )
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { press.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 300f)) }
                    },
                ) { change, dragAmount ->
                    change.consume()
                    totalDrag += dragAmount
                    lastPos = change.position
                    velocity = dragAmount
                    scope.launch {
                        dragR.snapTo(wrapModF(dragR.value - dragAmount.y / step, rows.toFloat()))
                        dragC.snapTo(wrapModF(dragC.value - dragAmount.x / step, cols.toFloat()))
                    }
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val maxDist = 1.9f
            val rc = dragR.value.roundToInt()
            val cc = dragC.value.roundToInt()
            val baseCellPx = BASE_CELL.toPx()
            val activeCellPx = ACTIVE_CELL.toPx() * press.value
            for (dr in -2..2) for (dc in -2..2) {
                val rawR = rc + dr
                val rawC = cc + dc
                val r = Math.floorMod(rawR, rows)
                val maxC = maxColOf(r)
                val c = Math.floorMod(rawC, maxC + 1)
                if (!valid(r, c)) continue
                val ddr = rawR - dragR.value
                val ddc = rawC - dragC.value
                val dist = hypot(ddr, ddc)
                if (dist > maxDist) continue
                val x = cx + ddc * step
                val y = cy + ddr * step
                val norm = (dist / maxDist).coerceAtMost(1f)
                val baseAlpha = (1f - norm).pow(2.6f)
                val cellBase = baseCellPx * (1f - norm * 0.5f)
                val boost = (1f - dist / 0.6f).coerceAtLeast(0f)
                val eased = boost * boost * (3f - 2f * boost)
                val cellPx = cellBase + (activeCellPx - cellBase) * eased
                val alpha = baseAlpha + (1f - baseAlpha) * eased
                drawGridNumber(r * 10 + c, x, y, cellPx, color, alpha)
            }
        }
    }
}

private fun DrawScope.drawGridNumber(
    value: Int,
    cx: Float,
    cy: Float,
    cellPx: Float,
    color: Color,
    alpha: Float,
) {
    if (alpha <= 0.02f || cellPx <= 0f) return
    val text = value.toString().padStart(2, '0')
    val cols = text.length * DotFont.W + (text.length - 1)
    val w = cols * cellPx
    val h = DotFont.H * cellPx
    val ox = cx - w / 2f + cellPx / 2f
    val oy = cy - h / 2f + cellPx / 2f
    val r = cellPx * 0.38f
    val c = color.copy(alpha = color.alpha * alpha)
    for (i in text.indices) {
        for (y in 0 until DotFont.H) for (x in 0 until DotFont.W) {
            if (!DotFont.on(text[i], x, y)) continue
            drawCircle(c, r, Offset(ox + (i * (DotFont.W + 1) + x) * cellPx, oy + y * cellPx))
        }
    }
}

/** Tendina grande e nera, sopra a tutto: solo il selettore, niente orologio in cima. */
@Composable
fun GridTimePickerPopup(
    hour: Int,
    minute: Int,
    onChange: (hour: Int, minute: Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = remember { MutableTransitionState(false) }
    LaunchedEffect(Unit) { visible.targetState = true }
    LaunchedEffect(visible.currentState, visible.isIdle) {
        if (!visible.currentState && visible.isIdle) onDismiss()
    }

    Dialog(
        onDismissRequest = { visible.targetState = false },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(Modifier.fillMaxSize()) {
            AnimatedVisibility(visibleState = visible, enter = fadeIn(tween(200)), exit = fadeOut(tween(200))) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                        ) { visible.targetState = false },
                )
            }
            AnimatedVisibility(
                visibleState = visible,
                enter = fadeIn(tween(320, easing = FastOutSlowInEasing)) +
                    scaleIn(initialScale = 0.92f, animationSpec = tween(320, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(220)) + scaleOut(targetScale = 0.9f, animationSpec = tween(220)),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(shape = RoundedCornerShape(30.dp), color = Color.Black) {
                    Column(
                        Modifier.padding(vertical = 28.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .width(36.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF1A1A1A)),
                        )
                        Spacer(Modifier.height(20.dp))
                        GridTimePicker(hour = hour, minute = minute, onChange = onChange)
                    }
                }
            }
        }
    }
}

package com.noalarm.glyph

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.noalarm.data.Store
import com.noalarm.ui.DotFont
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Pilota la Glyph Matrix mentre la sveglia suona: ora corrente a caratteri
 * dot-matrix, campanella pulsante, etichetta a scorrimento e conto alla
 * rovescia del rinvio. Su device senza Glyph resta inerte.
 */
object GlyphController {

    private const val FPS = 12L

    private var bridge: GlyphBridge? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val matrix = Matrix()

    private var mode = Mode.IDLE
    private var label = ""
    private var snoozeUntil = 0L
    private var frame = 0

    private enum class Mode { IDLE, RINGING, SNOOZED }

    @Synchronized
    fun ring(context: Context, label: String) {
        this.label = label.uppercase().filter(::printable)
        start(context, Mode.RINGING)
    }

    @Synchronized
    fun snoozed(context: Context, until: Long) {
        snoozeUntil = until
        start(context, Mode.SNOOZED)
    }

    @Synchronized
    fun stop() {
        mode = Mode.IDLE
        handler?.removeCallbacksAndMessages(null)
        runCatching { bridge?.close() }
        bridge = null
        handler = null
        thread?.quitSafely()
        thread = null
    }

    private fun start(context: Context, m: Mode) {
        if (!Store.settings.value.glyphEnabled) return
        mode = m
        frame = 0
        if (thread == null) {
            thread = HandlerThread("glyph").also { it.start() }
            handler = Handler(thread!!.looper)
        }
        if (bridge == null) {
            bridge = runCatching { GlyphBridge(context) { handler?.post(::tick) } }.getOrNull()
            if (bridge == null) stop()
        } else {
            handler?.post(::tick)
        }
    }

    private fun tick() {
        val b = bridge ?: return
        if (mode == Mode.IDLE) return
        render()
        runCatching { b.draw(matrix.pixels) }.onFailure { stop(); return }
        frame++
        handler?.postDelayed(::tick, 1000L / FPS)
    }

    private fun render() {
        matrix.clear()
        val s = Store.settings.value
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        if (mode == Mode.SNOOZED) {
            val left = ((snoozeUntil - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000
            matrix.textCentered("ZZ", 2, 90)
            matrix.textCentered(left.toString(), 10, 255)
            matrix.textCentered("MIN", 18, 70)
            return
        }
        // Ciclo di 4 s: 3 s di orologio, 1 s di campanella (con etichetta).
        if ((frame / FPS.toInt()) % 4 == 3) {
            val pulse = (60 + abs((frame % 12) - 6) * 32).coerceAtMost(255)
            matrix.bitmapCentered(Matrix.BELL, 2, pulse)
            if (label.isNotEmpty()) {
                val span = DotFont.width(label) + Matrix.SIZE
                matrix.text(label, Matrix.SIZE - (frame * 2 % span), 17, 120)
            }
        } else {
            val h = if (s.use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
            matrix.textCentered("%02d".format(h), 3)
            matrix.textCentered("%02d".format(now.minute), 15)
            if (now.second % 2 == 0) {          // i due punti lampeggiano al secondo
                matrix.set(12, 11, 200)
                matrix.set(12, 13, 200)
            }
        }
    }

    private fun printable(c: Char) = c.isLetterOrDigit() || c == ' ' || c == ':' || c == '-'
}

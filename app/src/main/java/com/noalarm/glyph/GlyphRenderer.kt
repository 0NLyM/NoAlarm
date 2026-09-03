package com.noalarm.glyph

import com.noalarm.data.GlyphStyle
import com.noalarm.ui.DotFont
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/**
 * Disegna nella [Matrix] cosa mostrare per una sveglia che suona o e' posticipata.
 * Puro (nessuno stato proprio): sia il canale app ([GlyphController], sempre attivo
 * mentre la sveglia suona) sia il canale toy ([NoAlarmGlyphToyService], attivo solo
 * quando l'utente ha scelto NoAlarm come Glyph Toy) lo richiamano con il proprio
 * contatore di fotogrammi, cosi' l'animazione e' identica su entrambi i canali.
 */
object GlyphRenderer {

    const val FPS = 12

    fun snoozed(matrix: Matrix, snoozeUntil: Long) {
        matrix.clear()
        val left = ((snoozeUntil - System.currentTimeMillis()).coerceAtLeast(0) + 59_999) / 60_000
        matrix.textCentered("ZZ", 2, 90)
        matrix.textCentered(left.toString(), 10, 255)
        matrix.textCentered("MIN", 18, 70)
    }

    fun ringing(matrix: Matrix, style: GlyphStyle, label: String, use24h: Boolean, frame: Int) {
        matrix.clear()
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        when (style) {
            GlyphStyle.CLOCK -> clock(matrix, now, use24h)
            GlyphStyle.BELL -> bell(matrix, frame)
            GlyphStyle.LABEL -> scrollingLabel(matrix, label, frame)
            GlyphStyle.COUNTDOWN -> countdown(matrix, frame)
            // Ciclo di 4 s: 3 s di orologio, 1 s di campanella con l'etichetta.
            GlyphStyle.CYCLE -> if ((frame / FPS) % 4 == 3) {
                bell(matrix, frame)
                scrollingLabel(matrix, label, frame)
            } else clock(matrix, now, use24h)
        }
    }

    fun clock(matrix: Matrix, now: ZonedDateTime, use24h: Boolean) {
        val h = if (use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
        matrix.textCentered("%02d".format(h), 3)
        matrix.textCentered("%02d".format(now.minute), 15)
        if (now.second % 2 == 0) {              // i due punti lampeggiano al secondo
            matrix.set(12, 11, 200)
            matrix.set(12, 13, 200)
        }
    }

    private fun bell(matrix: Matrix, frame: Int) {
        val pulse = (60 + abs((frame % 12) - 6) * 32).coerceAtMost(255)
        matrix.bitmapCentered(Matrix.BELL, 2, pulse)
    }

    private fun scrollingLabel(matrix: Matrix, label: String, frame: Int) {
        if (label.isEmpty()) return
        val span = DotFont.width(label) + Matrix.SIZE
        matrix.text(label, Matrix.SIZE - (frame * 2 % span), 17, 120)
    }

    /** Da quanto sta suonando: utile per capire a colpo d'occhio se e' in ritardo. */
    private fun countdown(matrix: Matrix, frame: Int) {
        val seconds = frame / FPS
        matrix.textCentered("SUONA", 2, 90)
        matrix.textCentered("%02d".format(seconds / 60), 10)
        matrix.textCentered("%02d".format(seconds % 60), 18, 140)
    }

    fun printable(c: Char) = c.isLetterOrDigit() || c == ' ' || c == ':' || c == '-'
}

package com.noalarm.glyph

import com.noalarm.ui.DotFont

/** Frame 25x25 della Glyph Matrix del Nothing Phone (3); ogni cella e' 0..255. */
class Matrix {

    val pixels = IntArray(SIZE * SIZE)

    fun clear() = pixels.fill(0)

    operator fun set(x: Int, y: Int, v: Int) {
        if (x in 0 until SIZE && y in 0 until SIZE) pixels[y * SIZE + x] = v.coerceIn(0, 255)
    }

    /** Scrive [text] con il font 5x7; [x] negativo per far scorrere il testo. */
    fun text(text: String, x: Int, y: Int, level: Int = 255, tracking: Int = 1) {
        text.forEachIndexed { i, c ->
            val ox = x + i * (DotFont.W + tracking)
            if (ox > SIZE || ox + DotFont.W < 0) return@forEachIndexed
            for (yy in 0 until DotFont.H) for (xx in 0 until DotFont.W) {
                if (DotFont.on(c, xx, yy)) set(ox + xx, y + yy, level)
            }
        }
    }

    /** Testo centrato orizzontalmente. */
    fun textCentered(text: String, y: Int, level: Int = 255, tracking: Int = 1) =
        text(text, (SIZE - DotFont.width(text, tracking)) / 2, y, level, tracking)

    fun bitmap(rows: List<String>, x: Int, y: Int, level: Int = 255) {
        rows.forEachIndexed { yy, row ->
            row.forEachIndexed { xx, c -> if (c != ' ' && c != '.') set(x + xx, y + yy, level) }
        }
    }

    fun bitmapCentered(rows: List<String>, y: Int, level: Int = 255) =
        bitmap(rows, (SIZE - (rows.maxOfOrNull { it.length } ?: 0)) / 2, y, level)

    /** Barra di avanzamento sull'ultima riga, [fraction] in 0..1. */
    fun progress(fraction: Float, y: Int = SIZE - 1, level: Int = 120) {
        val n = (fraction.coerceIn(0f, 1f) * SIZE).toInt()
        for (x in 0 until n) set(x, y, level)
    }

    companion object {
        const val SIZE = 25

        val BELL = listOf(
            "......#......",
            ".....###.....",
            "....#####....",
            "...#######...",
            "...#######...",
            "..#########..",
            "..#########..",
            ".###########.",
            "#############",
            "#############",
            ".............",
            ".....###.....",
            ".....###.....",
        )

    }
}

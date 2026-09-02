package com.noalarm.glyph

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import com.noalarm.ui.DotFont
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs

/** Cosa sta facendo la matrice, per la schermata di prova. */
data class GlyphStatus(
    /** null finche' non si e' provato ad aprire il ponte. */
    val available: Boolean? = null,
    val connected: Boolean = false,
    val registered: Boolean = false,
    val sent: Int = 0,
    val accepted: Int = 0,
    val rejected: Int = 0,
    val lastError: String? = null,
    val running: Boolean = false,
)

/**
 * Pilota la Glyph Matrix mentre la sveglia suona: ora corrente a caratteri
 * dot-matrix, campanella pulsante, etichetta a scorrimento e conto alla
 * rovescia del rinvio. Su device senza Glyph resta inerte.
 */
object GlyphController {

    private const val FPS = 12L

    /** Frame consecutivi rifiutati prima di arrendersi (5 s). */
    private const val MAX_FAILURES = 60

    /** Intervallo minimo fra due tentativi di ripresa del possesso. */
    private const val RECLAIM_COOLDOWN_MS = 2_000L

    private var bridge: GlyphBridge? = null
    private var thread: HandlerThread? = null
    private var handler: Handler? = null
    private val matrix = Matrix()

    private var mode = Mode.IDLE
    private var style = GlyphStyle.CYCLE
    private var label = ""
    private var snoozeUntil = 0L
    private var frame = 0
    private var failures = 0
    private var lastReclaim = 0L

    private val _status = MutableStateFlow(GlyphStatus())
    val status: StateFlow<GlyphStatus> = _status

    private enum class Mode { IDLE, RINGING, SNOOZED, TEST }

    @Synchronized
    fun ring(context: Context, label: String, style: GlyphStyle = GlyphStyle.CYCLE) {
        this.label = label.uppercase().filter(::printable)
        // Senza etichetta lo stile "etichetta" non avrebbe niente da mostrare.
        this.style = if (style == GlyphStyle.LABEL && this.label.isBlank()) GlyphStyle.CLOCK else style
        start(context, Mode.RINGING)
    }

    @Synchronized
    fun snoozed(context: Context, until: Long) {
        snoozeUntil = until
        start(context, Mode.SNOOZED)
    }

    /** Accende la matrice per la schermata di prova, finche' non la si ferma. */
    @Synchronized
    fun test(context: Context) {
        label = "NOALARM"
        style = GlyphStyle.CYCLE
        start(context, Mode.TEST)
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
        _status.value = _status.value.copy(running = false, connected = false, registered = false)
    }

    private fun start(context: Context, m: Mode) {
        if (m != Mode.TEST && !Store.settings.value.glyphEnabled) return
        mode = m
        frame = 0
        failures = 0
        lastReclaim = 0L
        _status.value = GlyphStatus(running = true)

        if (thread == null) {
            thread = HandlerThread("glyph").also { it.start() }
            handler = Handler(thread!!.looper)
        }
        if (bridge == null) {
            bridge = runCatching { GlyphBridge(context) { handler?.post(::tick) } }.getOrNull()
            _status.value = _status.value.copy(
                available = bridge != null,
                lastError = if (bridge == null) "Libreria com.nothing.ketchum assente" else null,
            )
            if (bridge == null) stop()
        } else {
            // removeCallbacks prima di ripartire: mai due cicli di disegno insieme.
            handler?.removeCallbacksAndMessages(null)
            handler?.post(::tick)
        }
    }

    private fun tick() {
        val b = bridge ?: return
        if (mode == Mode.IDLE) return
        render()

        val appChannel = Store.settings.value.glyphAppChannel
        val ok = b.draw(matrix.pixels, appChannel)
        if (ok) {
            failures = 0
        } else {
            failures++
            // Il possesso della matrice puo' essere revocato quando un Glyph Toy
            // torna in primo piano: si prova a riprenderlo, ma senza insistere a
            // ogni frame - register() ripetuto sembra chiudere la sessione.
            val now = System.currentTimeMillis()
            if (now - lastReclaim > RECLAIM_COOLDOWN_MS) {
                lastReclaim = now
                b.register()
            }
            if (failures > MAX_FAILURES) {
                stop()
                return
            }
        }

        frame++
        _status.value = _status.value.copy(
            available = true,
            connected = b.connected,
            registered = b.registered,
            sent = _status.value.sent + 1,
            accepted = _status.value.accepted + if (ok) 1 else 0,
            rejected = _status.value.rejected + if (ok) 0 else 1,
            lastError = b.lastError,
        )
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
        when (style) {
            GlyphStyle.CLOCK -> clock(now, s.use24h)
            GlyphStyle.BELL -> bell()
            GlyphStyle.LABEL -> scrollingLabel()
            GlyphStyle.COUNTDOWN -> countdownToNext()
            // Ciclo di 4 s: 3 s di orologio, 1 s di campanella con l'etichetta.
            GlyphStyle.CYCLE -> if ((frame / FPS.toInt()) % 4 == 3) {
                bell()
                scrollingLabel()
            } else clock(now, s.use24h)
        }
    }

    private fun clock(now: ZonedDateTime, use24h: Boolean) {
        val h = if (use24h) now.hour else (now.hour % 12).let { if (it == 0) 12 else it }
        matrix.textCentered("%02d".format(h), 3)
        matrix.textCentered("%02d".format(now.minute), 15)
        if (now.second % 2 == 0) {              // i due punti lampeggiano al secondo
            matrix.set(12, 11, 200)
            matrix.set(12, 13, 200)
        }
    }

    private fun bell() {
        val pulse = (60 + abs((frame % 12) - 6) * 32).coerceAtMost(255)
        matrix.bitmapCentered(Matrix.BELL, 2, pulse)
    }

    private fun scrollingLabel() {
        if (label.isEmpty()) return
        val span = DotFont.width(label) + Matrix.SIZE
        matrix.text(label, Matrix.SIZE - (frame * 2 % span), 17, 120)
    }

    /** Da quanto sta suonando: utile per capire a colpo d'occhio se e' in ritardo. */
    private fun countdownToNext() {
        val seconds = frame / FPS.toInt()
        matrix.textCentered("SUONA", 2, 90)
        matrix.textCentered("%02d".format(seconds / 60), 10)
        matrix.textCentered("%02d".format(seconds % 60), 18, 140)
    }

    private fun printable(c: Char) = c.isLetterOrDigit() || c == ' ' || c == ':' || c == '-'
}

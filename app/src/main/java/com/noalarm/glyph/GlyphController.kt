package com.noalarm.glyph

import android.content.Context
import android.os.Handler
import android.os.HandlerThread
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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
 * Pilota la Glyph Matrix mentre la sveglia suona, sul canale "app" della SDK
 * (`setAppMatrixFrame`): quello pensato per il controllo diretto da un'app che
 * non e' necessariamente il Glyph Toy selezionato, cosi' l'animazione si vede
 * anche se l'utente non ha scelto NoAlarm come toy attivo. Il canale del toy
 * vero e proprio - per quando lo ha scelto, e per il pulsante sul retro - e'
 * [NoAlarmGlyphToyService], che riusa lo stesso [GlyphRenderer].
 */
object GlyphController {

    /** Frame consecutivi rifiutati prima di arrendersi (5 s). */
    private const val MAX_FAILURES = GlyphRenderer.FPS * 5

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
        this.label = label.uppercase().filter(GlyphRenderer::printable)
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

        val s = Store.settings.value
        when (mode) {
            Mode.SNOOZED -> GlyphRenderer.snoozed(matrix, snoozeUntil)
            else -> GlyphRenderer.ringing(matrix, style, label, s.use24h, frame)
        }

        // La schermata di prova puo' forzare il canale toy (setMatrixFrame) per
        // capire quale dei due il dispositivo accetta davvero da un'app; la
        // sveglia vera usa sempre il canale app, come raccomandato dalla SDK.
        val appChannel = mode != Mode.TEST || s.glyphAppChannel
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
        handler?.postDelayed(::tick, 1000L / GlyphRenderer.FPS)
    }
}

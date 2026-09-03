package com.noalarm.glyph

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import com.noalarm.alarm.AlarmService
import com.noalarm.data.GlyphStyle
import com.noalarm.data.Store
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Glyph Toy di NoAlarm: quando l'utente lo seleziona in Impostazioni > Glyph
 * Interface > Glyph Toys, mostra l'ora sul retro del telefono e riceve gli
 * eventi del pulsante Glyph, l'unico modo di intercettarlo (non e' un tasto
 * Android: il sistema lo instrada solo al toy attivo in quel momento).
 *
 * Mentre una sveglia suona, sostituisce l'ora con la stessa animazione del
 * canale app ([GlyphController]) e interpreta il pulsante come rinvio/spegni:
 * - rilascio dopo una pressione (senza diventare "cambia toy") -> posticipa
 * - pressione lunga -> spegni
 * Il rilascio arriva solo se il toy era gia' selezionato quando si e' premuto,
 * quindi non si confonde mai con il gesto che passa al toy successivo.
 *
 * Non ha alcun effetto se NoAlarm non e' il toy scelto: in quel caso
 * l'animazione durante la sveglia arriva comunque, sul canale app.
 */
class NoAlarmGlyphToyService : Service() {

    private var glyphMatrixManager: GlyphMatrixManager? = null
    private val handler = Handler(Looper.getMainLooper())
    private val matrix = Matrix()
    private var frame = 0
    private var pressed = false

    private val callback = object : GlyphMatrixManager.Callback {
        override fun onServiceConnected(name: ComponentName?) {
            val gmm = glyphMatrixManager ?: return
            runCatching { gmm.register(Glyph.DEVICE_23112) }
            frame = 0
            handler.post(::tick)
        }

        override fun onServiceDisconnected(name: ComponentName?) = Unit
    }

    private val toyHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            if (msg.what != GlyphToy.MSG_GLYPH_TOY) return super.handleMessage(msg)
            when (msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)) {
                GlyphToy.EVENT_ACTION_DOWN -> pressed = true
                GlyphToy.EVENT_ACTION_UP -> {
                    if (pressed && AlarmService.ringing.value != 0L) {
                        AlarmService.snooze(applicationContext)
                    }
                    pressed = false
                }
                GlyphToy.EVENT_CHANGE -> {
                    if (AlarmService.ringing.value != 0L) AlarmService.dismiss(applicationContext)
                }
            }
        }
    }
    private val messenger = Messenger(toyHandler)

    override fun onBind(intent: Intent?): IBinder {
        glyphMatrixManager = GlyphMatrixManager.getInstance(applicationContext).also { it.init(callback) }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacksAndMessages(null)
        glyphMatrixManager?.let { gmm ->
            runCatching { gmm.turnOff() }
            runCatching { gmm.unInit() }
        }
        glyphMatrixManager = null
        return false
    }

    private fun tick() {
        val gmm = glyphMatrixManager ?: return
        val ringingId = AlarmService.ringing.value
        val use24h = Store.settings.value.use24h

        if (ringingId != 0L) {
            val alarm = Store.alarm(ringingId)
            GlyphRenderer.ringing(
                matrix,
                alarm?.glyphStyle ?: GlyphStyle.CYCLE,
                alarm?.label?.uppercase()?.filter(GlyphRenderer::printable) ?: "",
                use24h,
                frame,
            )
        } else {
            matrix.clear()
            GlyphRenderer.clock(matrix, ZonedDateTime.now(ZoneId.systemDefault()), use24h)
        }

        runCatching { gmm.setMatrixFrame(matrix.pixels) }
        frame++
        // L'orologio non ha bisogno di 12 fps: un frame al secondo gli basta,
        // la sveglia invece usa lo stesso ritmo del canale app.
        val delay = if (ringingId != 0L) 1000L / GlyphRenderer.FPS else 1000L
        handler.postDelayed(::tick, delay)
    }
}

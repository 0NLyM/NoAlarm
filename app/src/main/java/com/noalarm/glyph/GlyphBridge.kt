package com.noalarm.glyph

import android.content.ComponentName
import android.content.Context
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Unico punto di contatto con la Glyph Matrix SDK ufficiale di Nothing
 * (Nothing-Developer-Programme/GlyphMatrix-Developer-Kit, vendorizzata in
 * app/libs/glyph-matrix-sdk-2.0.aar). Su un device che non e' un Nothing Phone
 * il classloading di `com.nothing.ketchum.*` fallisce: intercettato da
 * [GlyphController], che lascia l'app inerte.
 *
 * Nessun metodo lancia verso l'alto: l'esito e l'ultimo errore si riportano,
 * perche' la diagnosi va mostrata all'utente nella schermata di prova.
 */
internal class GlyphBridge(context: Context, private val onReady: () -> Unit) {

    private val manager = GlyphMatrixManager.getInstance(context.applicationContext)

    @Volatile var connected = false
        private set

    @Volatile var registered = false
        private set

    @Volatile var lastError: String? = null
        private set

    private var announced = false

    init {
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                connected = true
                register()
                if (!announced) {
                    announced = true
                    onReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
                registered = false
            }
        })
    }

    /** Dichiara il possesso della matrice. Da chiamare con parsimonia. */
    fun register(): Boolean {
        registered = attempt { manager.register(Glyph.DEVICE_23112) } ?: false
        return registered
    }

    /**
     * @param appChannel usa `setAppMatrixFrame`: il canale per il controllo diretto
     *   da un'app che non e' necessariamente il Glyph Toy attivo. `setMatrixFrame`
     *   e' invece il canale dei Glyph Toy - vedi [NoAlarmGlyphToyService].
     */
    fun draw(frame: IntArray, appChannel: Boolean): Boolean {
        if (!connected) return false
        return attempt {
            if (appChannel) manager.setAppMatrixFrame(frame) else manager.setMatrixFrame(frame)
            true
        } ?: false
    }

    fun close() {
        connected = false
        registered = false
        attempt { manager.closeAppMatrix() }
        attempt { manager.turnOff() }
        attempt { manager.unInit() }
    }

    /** Esegue [block] conservando il messaggio dell'errore invece di propagarlo. */
    private fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (t: Throwable) {
        lastError = "${t::class.java.simpleName}: ${t.message ?: "senza messaggio"}"
        null
    }
}

package com.noalarm.glyph

import android.content.ComponentName
import android.content.Context
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager

/**
 * Unico punto di contatto con la SDK Glyph di Nothing.
 * Le classi `com.nothing.ketchum.*` sono `compileOnly`: esistono solo nel
 * framework dei Nothing Phone. Su qualsiasi altro device istanziare questa
 * classe lancia NoClassDefFoundError, che [GlyphController] intercetta.
 *
 * Niente qui lancia verso l'alto: si riporta l'esito e si conserva l'ultimo
 * errore, perche' la diagnosi va mostrata all'utente - da qui la SDK vera non
 * e' ispezionabile.
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
     * @param appChannel usa `setAppMatrixFrame` invece di `setMatrixFrame`.
     *   Quale dei due canali accetti i frame di un'app che non e' il Glyph Toy
     *   attivo non e' documentato: si sceglie dalle impostazioni e si verifica
     *   con la schermata di prova.
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

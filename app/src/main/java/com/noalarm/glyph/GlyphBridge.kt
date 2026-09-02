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
 * Nessun metodo lancia: il possesso della matrice puo' essere revocato dal
 * sistema in qualsiasi momento (per esempio quando un Glyph Toy torna in primo
 * piano), quindi qui si riporta solo esito vero/falso e la decisione se
 * insistere resta al chiamante.
 */
internal class GlyphBridge(context: Context, private val onReady: () -> Unit) {

    private val manager = GlyphMatrixManager.getInstance(context.applicationContext)

    @Volatile private var connected = false
    @Volatile private var announced = false

    init {
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                connected = true
                reclaim()
                // Solo la prima connessione avvia il ciclo di disegno: alle
                // riconnessioni successive il ciclo sta gia' girando.
                if (!announced) {
                    announced = true
                    onReady()
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                connected = false
            }
        })
    }

    /** (Ri)dichiara il possesso della matrice. */
    fun reclaim(): Boolean =
        connected && runCatching { manager.register(Glyph.DEVICE_23112) }.getOrDefault(false)

    /** true se il frame e' stato accettato. */
    fun draw(frame: IntArray): Boolean =
        connected && runCatching { manager.setMatrixFrame(frame); true }.getOrDefault(false)

    fun close() {
        connected = false
        runCatching { manager.turnOff() }
        runCatching { manager.unInit() }
    }
}

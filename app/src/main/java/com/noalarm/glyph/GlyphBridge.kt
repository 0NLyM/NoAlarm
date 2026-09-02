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
 */
internal class GlyphBridge(context: Context, private val onReady: () -> Unit) {

    private val manager = GlyphMatrixManager.getInstance(context.applicationContext)
    @Volatile private var registered = false

    init {
        manager.init(object : GlyphMatrixManager.Callback {
            override fun onServiceConnected(name: ComponentName?) {
                registered = runCatching { manager.register(Glyph.DEVICE_23112) }.getOrDefault(false)
                if (registered) onReady()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                registered = false
            }
        })
    }

    fun draw(frame: IntArray) {
        if (registered) manager.setMatrixFrame(frame)
    }

    fun close() {
        registered = false
        runCatching { manager.turnOff() }
        runCatching { manager.unInit() }
    }
}

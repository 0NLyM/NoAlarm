package com.noalarm.watch

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.wearable.Wearable
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/** Eco della sveglia sul watch: vibra e mostra spegni/posticipa, che rimandano l'esito al telefono. */
class RingActivity : ComponentActivity() {

    private var id: Long = 0L
    private var label = mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        current = this
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        applyIntent(intent)

        setContent {
            MaterialTheme {
                val text by label
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(text.ifBlank { "Sveglia" }, style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { respond(RingListenerService.PATH_SNOOZE); finish() }) { Text("Posticipa") }
                    Button(onClick = { respond(RingListenerService.PATH_DISMISS); finish() }) { Text("Spegni") }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntent(intent)
    }

    // Una nuova sveglia puo' arrivare mentre questa schermata e' gia' aperta
    // (launchMode singleInstance): aggiorna id/etichetta e vibra di nuovo.
    private fun applyIntent(intent: Intent) {
        id = intent.getLongExtra(EXTRA_ID, 0L)
        label.value = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        vibrate()
    }

    private fun vibrate() {
        val vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 200, 400, 1000), 0))
    }

    private fun respond(path: String) {
        if (id == 0L) return // "Prova" dalla schermata principale: nessun telefono da avvisare.
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.writeLong(id) }
        val payload = out.toByteArray()
        val messages = Wearable.getMessageClient(this)
        Wearable.getNodeClient(this).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node -> messages.sendMessage(node.id, path, payload) }
        }
    }

    override fun onDestroy() {
        if (current == this) current = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_LABEL = "label"
        private var current: RingActivity? = null

        fun ringIntent(context: Context, id: Long, label: String): Intent =
            Intent(context, RingActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_LABEL, label)

        fun testIntent(context: Context): Intent = ringIntent(context, 0L, "Prova")

        /** Chiamato quando il telefono smette di suonare: chiude l'eco se ancora aperta. */
        fun stop() {
            current?.finish()
        }
    }
}

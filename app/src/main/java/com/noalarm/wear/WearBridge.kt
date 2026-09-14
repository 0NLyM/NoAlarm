package com.noalarm.wear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.noalarm.alarm.AlarmService
import com.noalarm.data.Alarm
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Fa suonare/spegnere l'eco sul watch abbinato via Bluetooth diretto (RFCOMM
 * classico), non la Data Layer API di Google: quella richiede il watch
 * abbinato come "nodo" tramite l'app Wear OS di Google, che con alcuni
 * companion di terze parti (es. Mobvoi Health sui Ticwatch) non si crea mai,
 * indipendentemente da cosa gira sul telefono. Il Bluetooth di sistema invece
 * e' gia' li' appena i due dispositivi sono accoppiati, qualunque app li
 * abbia abbinati.
 *
 * Se il watch non e' raggiungibile in quel momento non succede nulla: e'
 * solo un'eco in piu' al polso, non la fonte di verita' della sveglia
 * (quella resta sempre AlarmService/AlarmManager sul telefono).
 */
object WearBridge {
    // Deve essere lo stesso valore di BridgeService.SERVICE_UUID sul watch:
    // moduli separati, nessun codice condiviso solo per questa costante.
    private val SERVICE_UUID: UUID = UUID.fromString("a4f7f228-8f1a-4b8e-9c7b-6b6c6f6e6f77")
    private const val ACTION_RING = 1
    private const val ACTION_STOP = 2
    private const val ACTION_SNOOZE = 3
    private const val ACTION_DISMISS = 4

    private val executor = Executors.newCachedThreadPool()
    @Volatile private var socket: BluetoothSocket? = null

    fun ringOnWatches(context: Context, alarm: Alarm) {
        if (!hasPermission(context)) return
        executor.execute {
            closeQuietly()
            val s = connect() ?: return@execute
            socket = s
            runCatching {
                DataOutputStream(s.outputStream).apply {
                    writeByte(ACTION_RING); writeLong(alarm.id); writeUTF(alarm.label); flush()
                }
                listenForReply(context, s)
            }
        }
    }

    fun stopOnWatches(context: Context) {
        val s = socket ?: return
        executor.execute {
            runCatching { DataOutputStream(s.outputStream).apply { writeByte(ACTION_STOP); flush() } }
            closeQuietly()
        }
    }

    /** Resta in ascolto sulla stessa connessione finche' non arriva un esito o si chiude. */
    private fun listenForReply(context: Context, s: BluetoothSocket) {
        val input = DataInputStream(s.inputStream)
        while (true) {
            val action = runCatching { input.readByte().toInt() }.getOrNull() ?: return
            when (action) {
                ACTION_DISMISS -> AlarmService.dismiss(context)
                ACTION_SNOOZE -> AlarmService.snooze(context)
            }
        }
    }

    // Tenta ogni dispositivo gia' accoppiato: solo quello con BridgeService in
    // ascolto sullo stesso UUID accetta, gli altri rifiutano subito la connessione.
    @SuppressLint("MissingPermission") // verificato da hasPermission()
    private fun connect(): BluetoothSocket? {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return null
        for (device in adapter.bondedDevices.orEmpty()) {
            val s = runCatching {
                device.createRfcommSocketToServiceRecord(SERVICE_UUID).also { it.connect() }
            }.getOrNull()
            if (s != null) return s
        }
        return null
    }

    private fun closeQuietly() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}

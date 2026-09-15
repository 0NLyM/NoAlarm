package com.noalarm.wear

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.noalarm.alarm.AlarmService
import com.noalarm.data.Alarm
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Cosa e' successo nell'ultimo tentativo di raggiungere il watch, per la schermata di prova. */
data class WearStatus(
    val checking: Boolean = false,
    val permission: Boolean? = null,
    val bondedDevices: List<String> = emptyList(),
    val connectedTo: String? = null,
    val method: String? = null,
    val elapsedMs: Long? = null,
    val sent: Boolean? = null,
    val ackReceived: Boolean? = null,
    val ackMs: Long? = null,
    val lastError: String? = null,
)

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

    private val _status = MutableStateFlow(WearStatus())
    val status: StateFlow<WearStatus> = _status

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

    /**
     * Prova manuale dalle Impostazioni: stessa strada di [ringOnWatches] (stesso
     * [connect], stesso [ACTION_RING] con id 0 come la "Prova" locale sul watch),
     * ma passo per passo in [status] invece di restare muta se qualcosa non va.
     * Il watch (BridgeService v1.4.18+) rimanda subito lo stesso [ACTION_RING]
     * come conferma di ricezione, senza bisogno di premere nulla sul quadrante.
     */
    fun test(context: Context) {
        if (!hasPermission(context)) {
            _status.value = WearStatus(permission = false, lastError = "Permesso Bluetooth non concesso")
            return
        }
        _status.value = WearStatus(checking = true, permission = true)
        executor.execute {
            closeQuietly()
            val start = System.currentTimeMillis()
            val s = connect()
            val connectMs = System.currentTimeMillis() - start
            if (s == null) {
                _status.value = _status.value.copy(checking = false, elapsedMs = connectMs)
                return@execute
            }
            socket = s
            val sent = runCatching {
                DataOutputStream(s.outputStream).apply {
                    writeByte(ACTION_RING); writeLong(0L); writeUTF("Prova NoAlarm"); flush()
                }
            }.isSuccess
            if (!sent) {
                _status.value = _status.value.copy(
                    checking = false, elapsedMs = connectMs, sent = false,
                    lastError = "Scrittura sulla connessione fallita",
                )
                return@execute
            }
            val ackStart = System.currentTimeMillis()
            val acked = runCatching {
                executor.submit(Callable { DataInputStream(s.inputStream).readByte().toInt() })
                    .get(5, TimeUnit.SECONDS) == ACTION_RING
            }.getOrDefault(false)
            _status.value = _status.value.copy(
                checking = false, elapsedMs = connectMs, sent = true,
                ackReceived = acked, ackMs = if (acked) System.currentTimeMillis() - ackStart else null,
                lastError = if (!acked) "Nessuna risposta dal watch in 5 s: l'app li' e' aggiornata e in esecuzione?" else null,
            )
            if (acked) listenForReply(context, s)
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
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            _status.value = _status.value.copy(lastError = "Bluetooth non disponibile su questo dispositivo")
            return null
        }
        // Richiede BLUETOOTH_SCAN su API 31+ (non chiesto: l'eco usa solo BLUETOOTH_CONNECT),
        // quindi lancia SecurityException - qui andrebbe persa dentro executor.execute()
        // e farebbe crashare l'app. Solo un'ottimizzazione facoltativa: si tenta comunque.
        runCatching { adapter.cancelDiscovery() }
        val devices = adapter.bondedDevices.orEmpty()
        _status.value = _status.value.copy(
            bondedDevices = devices.map { it.name ?: it.address }, connectedTo = null, method = null,
        )
        for (device in devices) {
            val name = device.name ?: device.address
            // Il canale diretto prima della SDP, non dopo: su stack di terze parti
            // (Ticwatch/Mobvoi) la ricerca SDP puo' restare bloccata per svariati
            // secondi prima di fallire, e aspettarla per intero come primo tentativo
            // fa arrivare l'eco quando la sveglia sul telefono e' ormai gia' spenta.
            val direct = runCatching { fallbackSocket(device).also { it.connect() } }.getOrNull()
            if (direct != null) {
                _status.value = _status.value.copy(connectedTo = name, method = "Canale diretto")
                return direct
            }
            val sdp = runCatching {
                device.createRfcommSocketToServiceRecord(SERVICE_UUID).also { it.connect() }
            }.getOrNull()
            if (sdp != null) {
                _status.value = _status.value.copy(connectedTo = name, method = "SDP (ripiego)")
                return sdp
            }
        }
        _status.value = _status.value.copy(
            lastError = if (devices.isEmpty()) "Nessun dispositivo Bluetooth accoppiato"
            else "Nessuno dei ${devices.size} dispositivi accoppiati risponde sull'UUID dell'eco",
        )
        return null
    }

    // Su alcuni stack Bluetooth di terze parti (es. Ticwatch/Mobvoi) la ricerca SDP
    // dell'UUID appena registrato da listenUsingRfcommWithServiceRecord() non si
    // risolve subito lato client. Bypassa la SDP e connette direttamente al canale 1,
    // il primo che l'OS assegna dinamicamente a quel metodo lato server.
    private fun fallbackSocket(device: BluetoothDevice): BluetoothSocket =
        device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
            .invoke(device, 1) as BluetoothSocket

    private fun closeQuietly() {
        runCatching { socket?.close() }
        socket = null
    }

    private fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
}

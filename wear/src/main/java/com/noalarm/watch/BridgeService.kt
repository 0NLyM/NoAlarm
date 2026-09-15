package com.noalarm.watch

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * In ascolto permanente del telefono via Bluetooth diretto (RFCOMM classico):
 * fa suonare/spegnere l'eco e rimanda al telefono rinvio/spegni. Sostituisce
 * la Data Layer API di Google, che con alcuni companion di terze parti
 * (es. Mobvoi Health) non abbina mai il watch come "nodo" - il Bluetooth di
 * sistema invece e' gia' li' appena i due dispositivi sono accoppiati.
 */
class BridgeService : Service() {

    private var serverSocket: BluetoothServerSocket? = null
    @Volatile private var running = false
    @Volatile private var activeSocket: BluetoothSocket? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_STATUS, "In ascolto del telefono", NotificationManager.IMPORTANCE_MIN)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_RING, "Eco sveglia", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    @SuppressLint("MissingPermission") // verificato da hasPermission()
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!hasPermission(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this, ID_STATUS,
            NotificationCompat.Builder(this, CH_STATUS)
                .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
                .setContentTitle("NoAlarm in ascolto del telefono")
                .setOngoing(true)
                .build(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        if (!running) {
            running = true
            Thread(::acceptLoop, "noalarm-bridge-accept").start()
        }
        return START_STICKY
    }

    // accept() si puo' richiamare piu' volte sullo stesso BluetoothServerSocket
    // per accettare connessioni successive: lo si ricrea solo se va in errore,
    // non a ogni sveglia (ri-registrare il record SDP non e' gratuito).
    @SuppressLint("MissingPermission")
    private fun acceptLoop() {
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return
        while (running) {
            val server = serverSocket ?: runCatching {
                adapter.listenUsingRfcommWithServiceRecord("NoAlarm", SERVICE_UUID)
            }.getOrNull()?.also { serverSocket = it } ?: return
            val socket = runCatching { server.accept() }.getOrNull()
            if (socket == null) {
                runCatching { server.close() }
                serverSocket = null
                continue
            }
            activeSocket = socket
            handle(socket)
        }
    }

    private fun handle(socket: BluetoothSocket) {
        val input = DataInputStream(socket.inputStream)
        while (running) {
            val action = runCatching { input.readByte().toInt() }.getOrNull() ?: break
            when (action) {
                ACTION_RING -> {
                    val id = runCatching { input.readLong() }.getOrNull() ?: break
                    val label = runCatching { input.readUTF() }.getOrNull() ?: ""
                    ring(id, label)
                    // Conferma di ricezione, usata dalla prova di connessione sul telefono
                    // (WearBridge.test()): per una sveglia vera il telefono la ignora.
                    respond(ACTION_RING)
                }
                ACTION_STOP -> {
                    NotificationManagerCompat.from(this).cancel(ID_RING)
                    RingActivity.stop()
                }
            }
        }
        runCatching { socket.close() }
        if (activeSocket == socket) activeSocket = null
    }

    /**
     * Un servizio in background non puo' piu' avviare direttamente un'Activity
     * a schermo intero (Android la blocca): serve una notifica a priorita'
     * massima con setFullScreenIntent, lo stesso meccanismo gia' usato sul
     * telefono per la schermata che suona davvero.
     */
    private fun ring(id: Long, label: String) {
        val full = PendingIntent.getActivity(
            this, 0, RingActivity.ringIntent(this, id, label),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(this, CH_RING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(label.ifBlank { "Sveglia" })
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setFullScreenIntent(full, true)
            .setContentIntent(full)
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(ID_RING, n) }
    }

    /** Rimanda al telefono, sulla connessione che ha fatto suonare questa sveglia, l'esito. */
    private fun respond(action: Int) {
        val socket = activeSocket ?: return
        Thread {
            runCatching { DataOutputStream(socket.outputStream).apply { writeByte(action); flush() } }
        }.start()
    }

    override fun onDestroy() {
        running = false
        runCatching { serverSocket?.close() }
        runCatching { activeSocket?.close() }
        if (instance == this) instance = null
        super.onDestroy()
    }

    companion object {
        // Deve essere lo stesso valore di WearBridge.SERVICE_UUID sul telefono:
        // moduli separati, nessun codice condiviso solo per questa costante.
        private val SERVICE_UUID: UUID = UUID.fromString("a4f7f228-8f1a-4b8e-9c7b-6b6c6f6e6f77")
        const val ACTION_RING = 1
        const val ACTION_STOP = 2
        const val ACTION_SNOOZE = 3
        const val ACTION_DISMISS = 4
        const val ID_RING = 1
        private const val ID_STATUS = 2
        private const val CH_STATUS = "status"
        private const val CH_RING = "ring"

        @Volatile private var instance: BridgeService? = null

        // Sotto la 31 BLUETOOTH_CONNECT non esiste come permesso runtime: il
        // Bluetooth e' coperto dai permessi normali (concessi all'installazione),
        // e checkSelfPermission su una stringa che l'OS non conosce puo' dare
        // "negato" anche quando in realta' non serve alcun consenso.
        private fun hasPermission(context: Context) =
            Build.VERSION.SDK_INT < 31 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
                    PackageManager.PERMISSION_GRANTED

        /**
         * Senza il permesso, avviare comunque il servizio e' un crash
         * garantito: startForegroundService() impone di chiamare
         * startForeground() a stretto giro, ma onStartCommand() senza
         * permesso si fermerebbe subito senza averlo mai chiamato. Meglio
         * non partire affatto - riparte da solo (MainActivity, BootReceiver)
         * appena il permesso c'e'.
         */
        fun start(context: Context) {
            if (hasPermission(context)) {
                ContextCompat.startForegroundService(context, Intent(context, BridgeService::class.java))
            }
        }

        fun respond(action: Int) = instance?.respond(action)
    }
}

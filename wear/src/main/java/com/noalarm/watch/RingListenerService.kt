package com.noalarm.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Riceve dal telefono l'ordine di far suonare o smettere l'eco sul watch. */
class RingListenerService : WearableListenerService() {

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CH_RING, "Eco sveglia", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_RING -> {
                val stream = DataInputStream(ByteArrayInputStream(event.data))
                ring(stream.readLong(), stream.readUTF())
            }
            PATH_STOP -> {
                NotificationManagerCompat.from(this).cancel(ID_RING)
                RingActivity.stop()
            }
        }
    }

    /**
     * Un servizio in background non puo' piu' avviare direttamente un'Activity
     * a schermo intero (Android la blocca): serve una notifica a priorita'
     * massima con setFullScreenIntent, lo stesso meccanismo gia' usato sul
     * telefono per la schermata che suona davvero (vedi NotificationHelper).
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

    companion object {
        // Stessi percorsi usati da WearBridge/PhoneWearService sul telefono:
        // nessun modulo condiviso solo per queste 4 costanti.
        const val PATH_RING = "/noalarm/ring"
        const val PATH_STOP = "/noalarm/stop"
        const val PATH_DISMISS = "/noalarm/dismiss"
        const val PATH_SNOOZE = "/noalarm/snooze"
        const val ID_RING = 1
        private const val CH_RING = "ring"
    }
}

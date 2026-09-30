package com.noalarm.watch

import android.os.PowerManager
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Riceve dal telefono l'ordine di far suonare o smettere l'eco sul watch. */
class RingListenerService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        when (event.path) {
            PATH_RING -> {
                val stream = DataInputStream(ByteArrayInputStream(event.data))
                val id = stream.readLong()
                val label = stream.readUTF()
                // A schermo spento la CPU puo' essere in doze: senza un risveglio
                // breve il sistema puo' rimandare l'avvio di RingActivity invece
                // di eseguirlo subito (stessa causa gia' isolata su questo
                // hardware per RingActivity nella precedente architettura RFCOMM,
                // vedi CLAUDE.md "Causa 8"). Si rilascia da sola dopo 10 s.
                getSystemService(PowerManager::class.java)
                    .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noalarm:ring")
                    .acquire(10_000L)
                startActivity(RingActivity.ringIntent(this, id, label))
            }
            PATH_STOP -> RingActivity.stop()
        }
    }

    companion object {
        // Stessi percorsi usati da WearBridge/PhoneWearService sul telefono:
        // nessun modulo condiviso solo per queste 4 costanti.
        const val PATH_RING = "/noalarm/ring"
        const val PATH_STOP = "/noalarm/stop"
        const val PATH_DISMISS = "/noalarm/dismiss"
        const val PATH_SNOOZE = "/noalarm/snooze"
    }
}

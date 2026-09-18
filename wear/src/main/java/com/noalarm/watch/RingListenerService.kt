package com.noalarm.watch

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

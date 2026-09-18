package com.noalarm.wear

import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.noalarm.alarm.AlarmService
import java.io.ByteArrayInputStream
import java.io.DataInputStream

/** Riceve dal watch lo spegni/posticipa dell'eco e li applica alla sveglia vera sul telefono. */
class PhoneWearService : WearableListenerService() {

    override fun onMessageReceived(event: MessageEvent) {
        val id = DataInputStream(ByteArrayInputStream(event.data)).readLong()
        if (AlarmService.ringing.value != id) return
        when (event.path) {
            PATH_DISMISS -> AlarmService.dismiss(this)
            PATH_SNOOZE -> AlarmService.snooze(this)
        }
    }

    companion object {
        const val PATH_DISMISS = "/noalarm/dismiss"
        const val PATH_SNOOZE = "/noalarm/snooze"
    }
}

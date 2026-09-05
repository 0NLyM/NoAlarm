package com.noalarm.wear

import android.content.Context
import com.google.android.gms.wearable.Wearable
import com.noalarm.data.Alarm
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

/**
 * Fa suonare/spegnere l'eco sul watch WearOS abbinato via Bluetooth, quando c'e'.
 * Se il watch non e' raggiungibile in quel momento non succede nulla: e' solo
 * un'eco in piu' al polso, non la fonte di verita' della sveglia (quella resta
 * sempre AlarmService/AlarmManager sul telefono).
 */
object WearBridge {
    const val PATH_RING = "/noalarm/ring"
    const val PATH_STOP = "/noalarm/stop"

    fun ringOnWatches(context: Context, alarm: Alarm) =
        send(context, PATH_RING, encode(alarm.id, alarm.label))

    fun stopOnWatches(context: Context) = send(context, PATH_STOP, ByteArray(0))

    private fun encode(id: Long, label: String): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { it.writeLong(id); it.writeUTF(label) }
        return out.toByteArray()
    }

    private fun send(context: Context, path: String, payload: ByteArray) {
        val messages = Wearable.getMessageClient(context)
        Wearable.getNodeClient(context).connectedNodes.addOnSuccessListener { nodes ->
            nodes.forEach { node -> messages.sendMessage(node.id, path, payload) }
        }
    }
}

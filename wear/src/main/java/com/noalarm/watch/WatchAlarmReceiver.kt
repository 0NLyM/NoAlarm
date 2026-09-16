package com.noalarm.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Sveglia programmata da WatchAlarmScheduler. Un BroadcastReceiver innescato
 * dall'AlarmManager e' esente dai limiti di avvio in background (a differenza
 * di un Service), quindi puo' aprire RingActivity a schermo intero anche a
 * schermo spento o processo altrimenti congelato, senza bisogno di una
 * connessione Bluetooth attiva in quel momento.
 */
class WatchAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(EXTRA_ID, 0L)
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()
        context.startActivity(RingActivity.ringIntent(context, id, label))
    }

    companion object {
        const val EXTRA_ID = "id"
        const val EXTRA_LABEL = "label"
    }
}

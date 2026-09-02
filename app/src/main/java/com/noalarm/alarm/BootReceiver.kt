package com.noalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noalarm.data.Store

/** Le sveglie non sopravvivono a riavvio, aggiornamento o cambio di fuso: le riprogramma. */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        NotificationHelper.createChannels(context)
        AlarmScheduler.syncAll(context)
    }
}

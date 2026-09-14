package com.noalarm.watch

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** BridgeService non sopravvive al riavvio del watch: lo riavvia. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        BridgeService.start(context)
    }
}

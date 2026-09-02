package com.noalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noalarm.data.Store

class AlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        when (intent.action) {
            AlarmScheduler.ACTION_BEDTIME -> {
                NotificationHelper.showBedtime(context, Store.settings.value.bedtimeReminderMinutes)
                AlarmScheduler.scheduleBedtime(context)
            }
            else -> {
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0L)
                val alarm = Store.alarm(id) ?: return
                // Il rinvio e' consumato: da qui in poi vale di nuovo l'orario base.
                if (alarm.snoozedUntil > 0) Store.updateAlarm(id) { it.copy(snoozedUntil = 0L) }
                if (alarm.skipNext) Store.updateAlarm(id) { it.copy(skipNext = false) }
                NotificationHelper.cancelSnoozed(context, id)
                AlarmService.ring(context, id, fresh = alarm.snoozedUntil == 0L)
            }
        }
    }
}

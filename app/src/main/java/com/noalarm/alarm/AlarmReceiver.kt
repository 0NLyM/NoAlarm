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
            AlarmScheduler.ACTION_REMIND -> {
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0L)
                Store.alarm(id)?.let { NotificationHelper.showAlarmReminder(context, it) }
            }
            else -> {
                val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0L)
                val alarm = Store.alarm(id) ?: return
                // Il rinvio e' consumato: da qui in poi vale di nuovo l'orario base.
                if (alarm.snoozedUntil > 0) Store.updateAlarm(id) { it.copy(snoozedUntil = 0L) }
                if (alarm.skipNext) Store.updateAlarm(id) { it.copy(skipNext = false) }
                NotificationHelper.cancelSnoozed(context, id)
                AlarmService.ring(context, id, fresh = alarm.snoozedUntil == 0L)
                // La notifica non ha piu' un fullScreenIntent (Wear OS non lo bridgea):
                // l'apertura a schermo intero sul telefono passa da qui, un
                // BroadcastReceiver innescato dall'AlarmManager e' esente dai
                // limiti di avvio in background e puo' avviare l'Activity diretta.
                context.startActivity(
                    Intent(context, AlarmActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                        .putExtra(AlarmScheduler.EXTRA_ID, id)
                )
            }
        }
    }
}

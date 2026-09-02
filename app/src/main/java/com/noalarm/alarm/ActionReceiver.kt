package com.noalarm.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.noalarm.clock.ClockService
import com.noalarm.data.Store

/** Punto unico per le azioni delle notifiche (sveglie, timer, cronometro). */
class ActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Store.init(context)
        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0L)
        val value = intent.getIntExtra(EXTRA_VALUE, 0)
        when (intent.action) {
            SNOOZE -> AlarmService.snooze(context, value.takeIf { it > 0 })
            DISMISS -> {
                if (AlarmService.ringing.value != 0L) {
                    AlarmService.dismiss(context)
                } else {
                    Store.updateAlarm(id) { it.copy(snoozedUntil = 0L) }
                    Store.alarm(id)?.let { AlarmScheduler.schedule(context, it) }
                    NotificationHelper.cancelSnoozed(context, id)
                    NotificationHelper.showUpcoming(context, AlarmScheduler.next())
                }
            }
            SKIP_NEXT -> {
                val alarm = Store.alarm(id) ?: return
                if (alarm.repeating) {
                    Store.updateAlarm(id) { it.copy(skipNext = true) }
                } else {
                    Store.updateAlarm(id) { it.copy(enabled = false) }
                }
                Store.alarm(id)?.let { AlarmScheduler.schedule(context, it) }
                NotificationHelper.showUpcoming(context, AlarmScheduler.next())
            }
            TIMER_STOP -> ClockService.timerStop(context, id)
            TIMER_ADD -> ClockService.timerAdd(context, id, value)
            TIMER_PAUSE -> ClockService.timerToggle(context, id)
            STOPWATCH_TOGGLE -> ClockService.stopwatchToggle(context)
            STOPWATCH_LAP -> ClockService.stopwatchLap(context)
            STOPWATCH_RESET -> ClockService.stopwatchReset(context)
        }
    }

    companion object {
        const val EXTRA_VALUE = "value"
        const val SNOOZE = "com.noalarm.action.N_SNOOZE"
        const val DISMISS = "com.noalarm.action.N_DISMISS"
        const val SKIP_NEXT = "com.noalarm.action.N_SKIP"
        const val TIMER_STOP = "com.noalarm.action.T_STOP"
        const val TIMER_ADD = "com.noalarm.action.T_ADD"
        const val TIMER_PAUSE = "com.noalarm.action.T_PAUSE"
        const val STOPWATCH_TOGGLE = "com.noalarm.action.S_TOGGLE"
        const val STOPWATCH_LAP = "com.noalarm.action.S_LAP"
        const val STOPWATCH_RESET = "com.noalarm.action.S_RESET"
    }
}

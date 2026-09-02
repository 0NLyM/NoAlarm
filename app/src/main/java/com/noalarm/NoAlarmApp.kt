package com.noalarm

import android.app.Application
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.alarm.NotificationHelper
import com.noalarm.data.Store

class NoAlarmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Store.init(this)
        NotificationHelper.createChannels(this)
        AlarmScheduler.syncAll(this)
    }
}

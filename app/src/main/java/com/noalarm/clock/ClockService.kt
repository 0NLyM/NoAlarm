package com.noalarm.clock

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.noalarm.Format
import com.noalarm.MainActivity
import com.noalarm.R
import com.noalarm.alarm.ActionReceiver
import com.noalarm.alarm.NotificationHelper
import com.noalarm.data.Store
import com.noalarm.data.TimerItem

/**
 * Notifica persistente di timer e cronometro, e suoneria dei timer scaduti.
 * Vive solo finche' c'e' qualcosa che scorre.
 */
class ClockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Store.init(this)
        when (intent?.action) {
            A_TIMER_STOP -> stopTimer(intent.getLongExtra(EXTRA_ID, 0))
            A_TIMER_ADD -> addMinutes(intent.getLongExtra(EXTRA_ID, 0), intent.getIntExtra(EXTRA_VALUE, 1))
            A_TIMER_TOGGLE -> toggleTimer(intent.getLongExtra(EXTRA_ID, 0))
            A_SW_TOGGLE -> toggleStopwatch()
            A_SW_LAP -> lap()
            A_SW_RESET -> resetStopwatch()
        }
        ensureForeground()
        tick()
        return START_STICKY
    }

    private fun ensureForeground() {
        if (started) return
        started = true
        ServiceCompat.startForeground(
            this, NotificationHelper.ID_CLOCK, build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        )
    }

    // --- ciclo -------------------------------------------------------------

    private fun tick() {
        handler.removeCallbacksAndMessages(null)
        val now = System.currentTimeMillis()

        Store.timers.value.filter { it.running && !it.expired && it.endAt <= now }.forEach { t ->
            Store.updateTimer(t.id) { it.copy(firedAt = now, running = false, remainingMs = 0) }
            startAlert()
        }

        val timers = Store.timers.value
        val sw = Store.stopwatch.value
        val busy = timers.any { it.running || it.expired } || sw.running

        if (!busy) {
            stopAlert()
            NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_STOPWATCH)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            started = false
            return
        }

        runCatching {
            NotificationManagerCompat.from(this).notify(NotificationHelper.ID_CLOCK, build())
            if (sw.running) {
                NotificationManagerCompat.from(this).notify(NotificationHelper.ID_STOPWATCH, buildStopwatch())
            } else {
                NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_STOPWATCH)
            }
        }
        handler.postDelayed(::tick, 1000)
    }

    // --- notifiche ---------------------------------------------------------

    private fun build(): Notification {
        val timers = Store.timers.value
        val expired = timers.firstOrNull { it.expired }
        val running = timers.filter { it.running }.minByOrNull { it.endAt }
        val open = openTab(MainActivity.TAB_TIMER)

        val b = NotificationCompat.Builder(this, if (expired != null) NotificationHelper.CH_ALARM else NotificationHelper.CH_CLOCK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentIntent(open)
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(expired == null)

        return when {
            expired != null -> b
                .setContentTitle(expired.label.ifBlank { "Timer scaduto" })
                .setContentText(Format.timer(expired.totalMs))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(open, true)
                .addAction(0, "INTERROMPI", NotificationHelper.action(this, ActionReceiver.TIMER_STOP, expired.id))
                .addAction(0, "+1 MIN", NotificationHelper.action(this, ActionReceiver.TIMER_ADD, expired.id, 1))
                .build()

            running != null -> b
                .setContentTitle(Format.timer(running.remaining()))
                .setContentText(running.label.ifBlank { if (timers.size > 1) "${timers.size} timer" else "Timer" })
                .addAction(0, "PAUSA", NotificationHelper.action(this, ActionReceiver.TIMER_PAUSE, running.id))
                .addAction(0, "+1 MIN", NotificationHelper.action(this, ActionReceiver.TIMER_ADD, running.id, 1))
                .build()

            else -> b.setContentTitle("NoAlarm").setOngoing(false).build()
        }
    }

    private fun buildStopwatch(): Notification {
        val sw = Store.stopwatch.value
        return NotificationCompat.Builder(this, NotificationHelper.CH_CLOCK)
            .setSmallIcon(R.drawable.ic_stat_alarm)
            .setContentTitle(Format.stopwatch(sw.elapsed()))
            .setContentText("Cronometro")
            .setContentIntent(openTab(MainActivity.TAB_STOPWATCH))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .addAction(0, "PAUSA", NotificationHelper.action(this, ActionReceiver.STOPWATCH_TOGGLE, 0))
            .addAction(0, "GIRO", NotificationHelper.action(this, ActionReceiver.STOPWATCH_LAP, 0))
            .build()
    }

    private fun openTab(tab: String): PendingIntent = PendingIntent.getActivity(
        this, tab.hashCode(),
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, tab),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    // --- azioni ------------------------------------------------------------

    private fun stopTimer(id: Long) {
        stopAlert()
        val t = Store.timers.value.firstOrNull { it.id == id } ?: return
        if (t.expired) Store.removeTimer(id)
        else Store.updateTimer(id) { it.copy(running = false, remainingMs = it.totalMs, endAt = 0) }
    }

    private fun addMinutes(id: Long, minutes: Int) {
        stopAlert()
        val now = System.currentTimeMillis()
        Store.updateTimer(id) {
            val base = if (it.expired) now else if (it.running) it.endAt else now + it.remainingMs
            it.copy(
                firedAt = 0,
                running = true,
                endAt = base + minutes * 60_000L,
                totalMs = it.totalMs + minutes * 60_000L,
            )
        }
    }

    private fun toggleTimer(id: Long) {
        val now = System.currentTimeMillis()
        Store.updateTimer(id) {
            if (it.running) it.copy(running = false, remainingMs = (it.endAt - now).coerceAtLeast(0), endAt = 0)
            else it.copy(running = true, endAt = now + it.remainingMs, firedAt = 0)
        }
    }

    private fun toggleStopwatch() {
        val now = System.currentTimeMillis()
        val sw = Store.stopwatch.value
        Store.setStopwatch(
            if (sw.running) sw.copy(startedAt = 0, accumulated = sw.elapsed(now)) else sw.copy(startedAt = now)
        )
    }

    private fun lap() {
        val sw = Store.stopwatch.value
        if (sw.running) Store.setStopwatch(sw.copy(laps = sw.laps + sw.elapsed()))
    }

    private fun resetStopwatch() = Store.setStopwatch(com.noalarm.data.Stopwatch())

    // --- suoneria timer ----------------------------------------------------

    private fun startAlert() {
        if (player != null) return
        val uri: Uri = Store.settings.value.timerSoundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@ClockService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 300), 0))
    }

    private fun stopAlert() {
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        stopAlert()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_VALUE = "value"
        private const val A_TIMER_STOP = "t_stop"
        private const val A_TIMER_ADD = "t_add"
        private const val A_TIMER_TOGGLE = "t_toggle"
        private const val A_SW_TOGGLE = "s_toggle"
        private const val A_SW_LAP = "s_lap"
        private const val A_SW_RESET = "s_reset"

        private fun send(c: Context, action: String, id: Long = 0, value: Int = 0) =
            c.startForegroundService(
                Intent(c, ClockService::class.java).setAction(action)
                    .putExtra(EXTRA_ID, id).putExtra(EXTRA_VALUE, value)
            )

        /** Riallinea la notifica dopo una modifica fatta dalla UI. */
        fun sync(c: Context) = send(c, "sync")

        fun timerStop(c: Context, id: Long) = send(c, A_TIMER_STOP, id)
        fun timerAdd(c: Context, id: Long, minutes: Int) = send(c, A_TIMER_ADD, id, minutes.coerceAtLeast(1))
        fun timerToggle(c: Context, id: Long) = send(c, A_TIMER_TOGGLE, id)
        fun stopwatchToggle(c: Context) = send(c, A_SW_TOGGLE)
        fun stopwatchLap(c: Context) = send(c, A_SW_LAP)
        fun stopwatchReset(c: Context) = send(c, A_SW_RESET)
    }
}

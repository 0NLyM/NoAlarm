package com.noalarm.alarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.noalarm.data.Alarm
import com.noalarm.data.KeyAction
import com.noalarm.data.Store
import com.noalarm.glyph.GlyphController
import com.noalarm.wear.WearBridge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.math.sqrt

/**
 * Tiene viva la sveglia che suona: audio, vibrazione, Glyph Matrix,
 * silenziamento automatico e gesti (capovolgi / scuoti / tasto power).
 */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private var volume = 0f
    private var screenOff: BroadcastReceiver? = null
    private var motion: SensorEventListener? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_SNOOZE -> {
                snooze(intent.getIntExtra(EXTRA_MINUTES, 0).takeIf { it > 0 })
                return START_NOT_STICKY
            }
            ACTION_DISMISS -> {
                dismiss(missed = false)
                return START_NOT_STICKY
            }
        }

        val id = intent?.getLongExtra(AlarmScheduler.EXTRA_ID, 0L) ?: 0L
        val alarm = Store.alarm(id) ?: run { stopSelf(); return START_NOT_STICKY }

        ringing.value = id
        if (intent?.getBooleanExtra(EXTRA_FRESH, true) != false) snoozeCount.value = 0
        ServiceCompat.startForeground(
            this,
            NotificationHelper.ID_RINGING,
            NotificationHelper.ringing(this, alarm),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
        )

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "noalarm:ring")
            .also { it.acquire(60 * 60 * 1000L) }

        play(alarm)
        if (alarm.vibrate) vibrate()
        if (alarm.glyph) GlyphController.ring(this, alarm.label, alarm.glyphStyle)
        WearBridge.ringOnWatches(this, alarm)
        listenScreenOff()
        listenMotion()

        if (alarm.autoSilenceMinutes > 0) {
            handler.postDelayed({ dismiss(missed = true) }, alarm.autoSilenceMinutes * 60_000L)
        }
        return START_STICKY
    }

    // --- suono ------------------------------------------------------------

    private fun play(alarm: Alarm) {
        val uri: Uri = alarm.soundUri?.let(Uri::parse)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmService, uri)
                isLooping = true
                prepare()
                start()
            }
        }.getOrNull()

        volume = if (alarm.gradualVolume) 0.05f else 1f
        player?.setVolume(volume, volume)
        if (alarm.gradualVolume) rampUp()
    }

    /** Volume crescente su 45 s, come "Aumenta gradualmente" di Google Clock. */
    private fun rampUp() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                val p = player ?: return
                volume = (volume + 0.02f).coerceAtMost(1f)
                runCatching { p.setVolume(volume, volume) }
                if (volume < 1f) handler.postDelayed(this, 900)
            }
        }, 900)
    }

    private fun vibrate() {
        vibrator = if (Build.VERSION.SDK_INT >= 31) {
            getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Vibrator::class.java)
        }
        val pattern = longArrayOf(0, 400, 200, 400, 1000)
        vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    // --- tasti fisici e gesti --------------------------------------------

    /** Il tasto power spegne lo schermo: e' l'unico modo per intercettarlo. */
    private fun listenScreenOff() {
        val action = Store.settings.value.powerKeyAction
        if (action == KeyAction.NONE) return
        screenOff = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = handle(action)
        }
        ContextCompat.registerReceiver(
            this, screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF), ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun listenMotion() {
        val s = Store.settings.value
        if (s.flipAction == KeyAction.NONE && s.shakeAction == KeyAction.NONE) return
        val sm = getSystemService(SensorManager::class.java) ?: return
        val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        motion = object : SensorEventListener {
            private var faceUp = false
            private var shakes = 0
            private var lastShake = 0L

            override fun onSensorChanged(e: SensorEvent) {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                if (s.flipAction != KeyAction.NONE) {
                    if (z > 8f) faceUp = true
                    if (faceUp && z < -8f) {
                        faceUp = false
                        handle(s.flipAction)
                        return
                    }
                }
                if (s.shakeAction != KeyAction.NONE) {
                    val g = sqrt(x * x + y * y + z * z)
                    val now = System.currentTimeMillis()
                    if (g > 18f && now - lastShake > 250) {
                        shakes = if (now - lastShake < 1500) shakes + 1 else 1
                        lastShake = now
                        if (shakes >= 3) handle(s.shakeAction)
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(motion, accel, SensorManager.SENSOR_DELAY_UI)
    }

    private fun handle(action: KeyAction) = when (action) {
        KeyAction.SNOOZE -> snooze(null)
        KeyAction.DISMISS -> dismiss(missed = false)
        else -> Unit
    }

    // --- esiti -------------------------------------------------------------

    private fun snooze(minutes: Int?) {
        val alarm = Store.alarm(ringing.value) ?: return stopSelf()
        val m = minutes ?: alarm.snoozeMinutes
        val until = System.currentTimeMillis() + m * 60_000L
        snoozeCount.value += 1
        Store.updateAlarm(alarm.id) { it.copy(snoozedUntil = until) }
        Store.alarm(alarm.id)?.let { AlarmScheduler.schedule(this, it) }
        NotificationHelper.showSnoozed(this, alarm, until)
        if (alarm.glyph) GlyphController.snoozed(this, until)
        // La matrice mostra il countdown per 10 s, poi si spegne per non consumare.
        handler.postDelayed({ GlyphController.stop() }, 10_000)
        finish(keepGlyph = true)
    }

    private fun dismiss(missed: Boolean) {
        val alarm = Store.alarm(ringing.value)
        if (alarm != null) {
            if (missed) NotificationHelper.showMissed(this, alarm)
            Store.updateAlarm(alarm.id) {
                it.copy(
                    snoozedUntil = 0L,
                    skipNext = false,
                    enabled = if (it.repeating) it.enabled else false,
                )
            }
            Store.alarm(alarm.id)?.let { AlarmScheduler.schedule(this, it) }
            NotificationHelper.cancelSnoozed(this, alarm.id)
        }
        snoozeCount.value = 0
        finish(keepGlyph = false)
    }

    private fun finish(keepGlyph: Boolean) {
        ringing.value = 0L
        if (!keepGlyph) GlyphController.stop()
        WearBridge.stopOnWatches(this)
        NotificationHelper.showUpcoming(this, AlarmScheduler.next())
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        runCatching { player?.stop() }
        player?.release()
        player = null
        vibrator?.cancel()
        screenOff?.let { runCatching { unregisterReceiver(it) } }
        motion?.let { runCatching { getSystemService(SensorManager::class.java).unregisterListener(it) } }
        wakeLock?.let { if (it.isHeld) it.release() }
        ringing.value = 0L
        NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_RINGING)
        super.onDestroy()
    }

    companion object {
        const val ACTION_SNOOZE = "com.noalarm.action.SNOOZE"
        const val ACTION_DISMISS = "com.noalarm.action.DISMISS"
        const val EXTRA_MINUTES = "minutes"

        private const val EXTRA_FRESH = "fresh"

        /** Id della sveglia che sta suonando, 0 se nessuna. */
        val ringing = MutableStateFlow(0L)

        /** Quante volte questa sveglia e' gia' stata posticipata. */
        val snoozeCount = MutableStateFlow(0)

        fun ring(c: Context, id: Long, fresh: Boolean = true) = c.startForegroundService(
            Intent(c, AlarmService::class.java)
                .putExtra(AlarmScheduler.EXTRA_ID, id)
                .putExtra(EXTRA_FRESH, fresh)
        )

        fun snooze(c: Context, minutes: Int? = null) = c.startService(
            Intent(c, AlarmService::class.java).setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_MINUTES, minutes ?: 0)
        )

        fun dismiss(c: Context) = c.startService(
            Intent(c, AlarmService::class.java).setAction(ACTION_DISMISS)
        )
    }
}

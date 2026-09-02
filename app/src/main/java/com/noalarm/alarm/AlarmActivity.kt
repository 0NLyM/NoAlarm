package com.noalarm.alarm

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AlarmOff
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Snooze
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.data.Alarm
import com.noalarm.data.KeyAction
import com.noalarm.data.Store
import com.noalarm.ui.DotText
import com.noalarm.ui.DotButton
import com.noalarm.ui.DotIconButton
import com.noalarm.ui.rememberNow
import com.noalarm.ui.theme.NoAlarmTheme
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Schermata a tutto schermo mentre la sveglia suona. Oltre a spegni/posticipa
 * offre i pulsanti +/- per cambiare i minuti di rinvio prima di posticipare,
 * e risponde ai tasti fisici secondo le impostazioni.
 */
class AlarmActivity : ComponentActivity() {

    private var snoozeMinutes = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Store.init(this)
        enableEdgeToEdge()
        showOverLockScreen()
        // Il tasto Indietro non deve poter chiudere la sveglia.
        onBackPressedDispatcher.addCallback(this) { }

        val id = intent.getLongExtra(AlarmScheduler.EXTRA_ID, 0L)
        val alarm = Store.alarm(id) ?: Store.alarm(AlarmService.ringing.value)
        if (alarm == null) {
            finish()
            return
        }
        snoozeMinutes = alarm.snoozeMinutes

        setContent {
            NoAlarmTheme(dark = true) {
                val ringing by AlarmService.ringing.collectAsStateWithLifecycle()
                LaunchedEffect(ringing) { if (ringing == 0L) finishAndRemoveTask() }
                Ringing(
                    alarm = alarm,
                    onSnoozeChange = { snoozeMinutes = it },
                    onSnooze = { AlarmService.snooze(this, it) },
                    onDismiss = { AlarmService.dismiss(this) },
                )
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )
    }

    /** Tasti volume: l'unico modo per intercettare un tasto fisico dall'app. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isVolume = keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_VOLUME_MUTE
        if (!isVolume) return super.onKeyDown(keyCode, event)

        return when (Store.settings.value.volumeKeyAction) {
            KeyAction.SNOOZE -> { AlarmService.snooze(this, snoozeMinutes); true }
            KeyAction.DISMISS -> { AlarmService.dismiss(this); true }
            KeyAction.NONE -> true                       // assorbiti: niente cambio volume
            KeyAction.VOLUME -> super.onKeyDown(keyCode, event)
        }
    }

}

@Composable
private fun Ringing(
    alarm: Alarm,
    onSnoozeChange: (Int) -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val settings by Store.settings.collectAsStateWithLifecycle()
    val snoozes by AlarmService.snoozeCount.collectAsStateWithLifecycle()
    val now = rememberNow(1000L)
    var minutes by remember { mutableIntStateOf(alarm.snoozeMinutes) }
    val pulse by rememberInfiniteTransition("pulse").animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "pulse",
    )
    val time = ZonedDateTime.ofInstant(Instant.ofEpochMilli(now), ZoneId.systemDefault())
    val outOfSnoozes = alarm.snoozeLimit > 0 && snoozes >= alarm.snoozeLimit

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(72.dp))
                Text(
                    alarm.label.ifBlank { "Sveglia" }.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.alpha(pulse),
                )
                Spacer(Modifier.height(24.dp))
                DotText(
                    Format.clock(time, settings.use24h),
                    Modifier.fillMaxWidth().height(110.dp),
                    cell = 14.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "${Format.dayLabel(time.dayOfWeek, false)} ${time.dayOfMonth}".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(1f))

            // Regolazione del rinvio con i pulsanti, alla Samsung.
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (outOfSnoozes) "RINVII ESAURITI" else "RINVIA DI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    val step = alarm.snoozeStepMinutes
                    val less = {
                        minutes = (minutes - step).coerceAtLeast(alarm.snoozeMinMinutes)
                        onSnoozeChange(minutes)
                    }
                    val canLess = !outOfSnoozes && minutes > alarm.snoozeMinMinutes
                    if (step > 1) DotButton("-$step", less, size = 56, enabled = canLess)
                    else DotIconButton(Icons.Outlined.Remove, "Rinvia di meno", less, size = 56, enabled = canLess)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        DotText(
                            minutes.toString(),
                            Modifier.size(64.dp, 48.dp),
                            cell = 6.dp,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text("MIN", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val more = {
                        minutes = (minutes + step).coerceAtMost(alarm.snoozeMaxMinutes)
                        onSnoozeChange(minutes)
                    }
                    val canMore = !outOfSnoozes && minutes < alarm.snoozeMaxMinutes
                    if (step > 1) DotButton("+$step", more, size = 56, enabled = canMore)
                    else DotIconButton(Icons.Outlined.Add, "Rinvia di piu'", more, size = 56, enabled = canMore)
                }
                if (snoozes > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "GIA' RINVIATA $snoozes ${if (snoozes == 1) "VOLTA" else "VOLTE"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            // Ben distanti: al buio, appena svegli, non ci si deve sbagliare.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DotIconButton(
                    Icons.Outlined.Snooze,
                    "Posticipa di $minutes minuti",
                    onClick = { onSnooze(minutes) },
                    size = 104,
                    enabled = !outOfSnoozes,
                )
                DotIconButton(
                    Icons.Outlined.AlarmOff,
                    "Spegni la sveglia",
                    onClick = onDismiss,
                    size = 104,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

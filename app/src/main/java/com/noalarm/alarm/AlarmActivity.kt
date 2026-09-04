package com.noalarm.alarm

import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.Format
import com.noalarm.data.Alarm
import com.noalarm.data.KeyAction
import com.noalarm.data.Store
import com.noalarm.glyph.Matrix
import com.noalarm.ui.DotButton
import com.noalarm.ui.DotIconButton
import com.noalarm.ui.DotPillButton
import com.noalarm.ui.DotText
import com.noalarm.ui.GlyphStylePreview
import com.noalarm.ui.rememberNow
import com.noalarm.ui.theme.NoAlarmTheme
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.hypot

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
                // null finche' l'utente non agisce (o il servizio si ferma da solo,
                // per esempio dal pulsante Glyph o dal silenziamento automatico):
                // in quel caso si mostra comunque la stessa animazione di chiusura.
                var closing by remember { mutableStateOf<ClosingReason?>(null) }

                LaunchedEffect(ringing) {
                    if (ringing == 0L && closing == null) closing = ClosingReason.Dismissed
                }

                // compositingStrategy = Offscreen: serve perche' il "buco" trasparente
                // disegnato da ClosingOverlay in chiusura possa bucare anche Ringing()
                // sotto di se' (nella stessa finestra ora trasparente), invece di
                // fermarsi al primo livello opaco e restare nero.
                Box(Modifier.fillMaxSize().graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)) {
                    Ringing(
                        alarm = alarm,
                        onSnoozeChange = { snoozeMinutes = it },
                        onSnooze = { m ->
                            closing = ClosingReason.Snoozed(m)
                            AlarmService.snooze(this@AlarmActivity, m)
                        },
                        onDismiss = {
                            closing = ClosingReason.Dismissed
                            AlarmService.dismiss(this@AlarmActivity)
                        },
                    )
                    closing?.let { ClosingOverlay(it, onFinished = ::finishAndRemoveTask) }
                }
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

/** Perche' la schermata si sta chiudendo: decide cosa mostra l'animazione finale. */
private sealed interface ClosingReason {
    data class Snoozed(val minutes: Int) : ClosingReason
    data object Dismissed : ClosingReason
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
                Spacer(Modifier.height(56.dp))
                Text(
                    alarm.label.ifBlank { "Sveglia" }.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.alpha(pulse),
                )
                Spacer(Modifier.height(20.dp))
                DotText(
                    Format.clock(time, settings.use24h),
                    Modifier.fillMaxWidth().height(96.dp),
                    cell = 12.dp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "${Format.dayLabel(time.dayOfWeek, false)} ${time.dayOfMonth}".uppercase(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.weight(0.6f))

            // L'animazione che sta suonando davvero sul retro del telefono,
            // sempre in bianco su nero: qui non e' un'anteprima, e' lo specchio.
            if (alarm.glyph) {
                GlyphStylePreview(
                    style = alarm.glyphStyle,
                    label = alarm.label,
                    modifier = Modifier.size((Matrix.SIZE * 4).dp),
                    cell = 4.dp,
                    onColor = Color.White,
                    offColor = Color.Black,
                    backgroundColor = Color.Black,
                )
                Spacer(Modifier.weight(0.5f))
            }

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

            // Larghe e con il testo per intero: al buio, appena svegli, un'icona
            // sola fra "posticipa" e "spegni" e' facile da confondere.
            Row(
                Modifier.fillMaxWidth().padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DotPillButton(
                    "Posticipa",
                    onClick = { onSnooze(minutes) },
                    enabled = !outOfSnoozes,
                )
                DotPillButton(
                    "Spegni",
                    onClick = onDismiss,
                    color = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary,
                )
            }
        }
    }
}

/**
 * Chiusura come Google Clock: un cerchio nero che si espande dal centro fino
 * a coprire tutto lo schermo con la conferma gia' visibile, poi - invertito
 * rispetto all'apertura - si allarga un foro che non scopre di nuovo questa
 * schermata ma quello che c'e' davvero sotto la sveglia (l'app che stava
 * girando prima, o la schermata di blocco): la finestra e' trasparente
 * (Theme.NoAlarm.Ringing) apposta per questo, ClosingOverlay si limita a
 * bucarla. Solo a foro completo l'activity si chiude, senza alcun taglio
 * netto. Se la sveglia e' stata posticipata mostra per quanto, cosi' non
 * resta il dubbio se il tocco sia andato a segno.
 */
@Composable
private fun ClosingOverlay(reason: ClosingReason, onFinished: () -> Unit) {
    val progress = remember { Animatable(0f) }     // 0 = invisibile, 1 = schermo coperto
    val hole = remember { Animatable(0f) }          // 0 = nessun foro, 1 = foro a schermo intero
    val textAlpha = remember { Animatable(1f) }     // visibile fin dal primo frame

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        delay(650)
        textAlpha.animateTo(0f, tween(180))
        hole.animateTo(1f, tween(600, easing = FastOutSlowInEasing))
        onFinished()
    }

    Canvas(Modifier.fillMaxSize()) {
        val maxRadius = hypot(size.width, size.height) / 2f
        drawCircle(Color.Black, radius = progress.value * maxRadius)
        if (hole.value > 0f) {
            drawCircle(Color.Black, radius = hole.value * maxRadius, blendMode = BlendMode.Clear)
        }
    }

    if (textAlpha.value > 0f) {
        Box(
            Modifier.fillMaxSize().alpha(textAlpha.value),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    if (reason is ClosingReason.Snoozed) Icons.Outlined.Snooze else Icons.Outlined.AlarmOff,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    if (reason is ClosingReason.Snoozed) "POSTICIPATA DI ${reason.minutes} MIN" else "SVEGLIA SPENTA",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                )
            }
        }
    }
}

package com.noalarm

import android.Manifest
import android.content.Intent
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.noalarm.alarm.AlarmScheduler
import com.noalarm.data.BarAppearance
import com.noalarm.data.Store
import com.noalarm.ui.AlarmScreen
import com.noalarm.ui.CalendarScreen
import com.noalarm.ui.SettingsScreen
import com.noalarm.ui.StopwatchScreen
import com.noalarm.ui.TimerScreen
import com.noalarm.ui.WorldClockScreen
import com.noalarm.ui.theme.NoAlarmTheme

class MainActivity : ComponentActivity() {

    private var pendingTab by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Store.init(this)
        pendingTab = tabFrom(intent)

        val ask = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

        setContent {
            NoAlarmTheme {
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                    // Senza questo permesso speciale le sveglie possono ritardare o non
                    // suonare affatto: va chiesto subito, non solo se l'utente trova la riga
                    // in Impostazioni.
                    if (Build.VERSION.SDK_INT >= 31 && !AlarmScheduler.canScheduleExact(this@MainActivity)) {
                        // Niente setData qui: a differenza di ACTION_APPLICATION_DETAILS_SETTINGS
                        // o ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS, questa action non vuole
                        // un Uri "package:..." - il sistema individua l'app chiamante da se', e
                        // un data extra fa fallire silenziosamente la risoluzione dell'intent.
                        this@MainActivity.startActivity(Intent(AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM))
                    }
                }
                Home(pendingTab) { pendingTab = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingTab = tabFrom(intent)
    }

    private fun tabFrom(intent: Intent?): String? = when (intent?.action) {
        AlarmClock.ACTION_SHOW_ALARMS, AlarmClock.ACTION_SET_ALARM -> TAB_ALARM
        AlarmClock.ACTION_SHOW_TIMERS, AlarmClock.ACTION_SET_TIMER -> TAB_TIMER
        else -> intent?.getStringExtra(EXTRA_TAB)
    }

    companion object {
        const val EXTRA_TAB = "tab"
        const val TAB_ALARM = "alarm"
        const val TAB_CLOCK = "clock"
        const val TAB_TIMER = "timer"
        const val TAB_STOPWATCH = "stopwatch"
        const val TAB_CALENDAR = "calendar"
    }
}

private data class Tab(val key: String, val title: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(MainActivity.TAB_ALARM, "Sveglia", Icons.Outlined.Alarm),
    Tab(MainActivity.TAB_CLOCK, "Orologio", Icons.Outlined.Public),
    Tab(MainActivity.TAB_TIMER, "Timer", Icons.Outlined.HourglassEmpty),
    Tab(MainActivity.TAB_STOPWATCH, "Cronometro", Icons.Outlined.Timer),
    Tab(MainActivity.TAB_CALENDAR, "Calendario", Icons.Outlined.CalendarMonth),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Home(requestedTab: String?, onTabConsumed: () -> Unit) {
    var current by rememberSaveable { mutableStateOf(MainActivity.TAB_ALARM) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val s by Store.settings.collectAsStateWithLifecycle()
    // Cattura una copia di cio' che sta dietro alla pillola del menu, cosi'
    // NothingBottomBar puo' sfocarla per davvero: Compose non ha un
    // backdrop-filter nativo, questo layer registrato e' l'unico modo.
    val backdrop = rememberGraphicsLayer()

    LaunchedEffect(requestedTab) {
        requestedTab?.let { current = it; settingsOpen = false; onTabConsumed() }
    }

    val tab = remember(current) { tabs.firstOrNull { it.key == current } ?: tabs[0] }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Si registra una volta sola e si ridisegna dal layer, non con una
                    // seconda drawContent(): e' il modo corretto per riusare lo stesso
                    // buffer sia per lo schermo sia per il campione che sfoca la pillola.
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                },
        ) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    TopAppBar(
                        title = { Text(tab.title.uppercase(), style = MaterialTheme.typography.labelLarge) },
                        actions = {
                            IconButton(onClick = { settingsOpen = true }) {
                                Icon(Icons.Outlined.Settings, "Impostazioni")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                },
            ) { inset ->
                Box(Modifier.fillMaxSize().padding(inset)) {
                    when (current) {
                        MainActivity.TAB_ALARM -> AlarmScreen()
                        MainActivity.TAB_CLOCK -> WorldClockScreen()
                        MainActivity.TAB_TIMER -> TimerScreen()
                        MainActivity.TAB_STOPWATCH -> StopwatchScreen()
                        else -> CalendarScreen()
                    }
                }
            }
        }
        NothingBottomBar(
            selected = current,
            appearance = s.barAppearance,
            backdrop = backdrop,
            onSelect = { current = it },
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // Livello sopra a tutto, menu inferiore compreso: e' una schermata a se',
        // non un tab, quindi Indietro la chiude invece di uscire dall'app.
        if (settingsOpen) {
            BackHandler { settingsOpen = false }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    topBar = {
                        TopAppBar(
                            title = { Text("IMPOSTAZIONI", style = MaterialTheme.typography.labelLarge) },
                            navigationIcon = {
                                IconButton(onClick = { settingsOpen = false }) {
                                    Icon(Icons.Outlined.Close, "Chiudi")
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.background,
                                titleContentColor = MaterialTheme.colorScheme.onBackground,
                            ),
                        )
                    },
                ) { inset -> Box(Modifier.fillMaxSize().padding(inset)) { SettingsScreen() } }
            }
        }
    }
}

/**
 * Pillola fluttuante in stile Nothing OS 5, non piu' una barra a tutta
 * larghezza: solida o "vetro" a seconda della scelta nelle impostazioni.
 * La tinta e' sempre la stessa nelle due modalita' - a cambiare e' solo la
 * sfocatura di cio' che c'e' dietro, non il colore della pillola.
 */
@Composable
private fun NothingBottomBar(
    selected: String?,
    appearance: BarAppearance,
    backdrop: GraphicsLayer,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blur = appearance == BarAppearance.BLUR
    val tint = MaterialTheme.colorScheme.surfaceContainerHigh
    var barPosition by remember { mutableStateOf(Offset.Zero) }
    val blurEffect = remember {
        RenderEffect.createBlurEffect(28f, 28f, Shader.TileMode.CLAMP).asComposeRenderEffect()
    }

    Box(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(50))
            .onGloballyPositioned { barPosition = it.positionInRoot() },
    ) {
        if (blur) {
            // Il vero blur gaussiano: una copia di cio' che sta dietro la
            // pillola (registrata piu' in alto, in Home), sfocata e ritagliata
            // alla sua stessa forma - non una tinta trasparente sopra i pixel
            // grezzi, che a schermo fermo non "sembra" vetro.
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { renderEffect = blurEffect }
                    .drawWithContent {
                        translate(-barPosition.x, -barPosition.y) { drawLayer(backdrop) }
                    },
            )
            Box(Modifier.matchParentSize().background(tint.copy(alpha = 0.55f)))
            Box(
                Modifier.matchParentSize().border(
                    1.dp,
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                    RoundedCornerShape(50),
                ),
            )
        } else {
            Box(Modifier.matchParentSize().background(tint))
        }

        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { t ->
                val isSelected = t.key == selected
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) MaterialTheme.colorScheme.secondary else Color.Transparent)
                        .clickable { onSelect(t.key) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        t.icon,
                        t.title,
                        tint = if (isSelected) MaterialTheme.colorScheme.onSecondary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

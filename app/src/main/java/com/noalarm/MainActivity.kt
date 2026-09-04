package com.noalarm

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.AlarmClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    var settings by rememberSaveable { mutableStateOf(false) }
    val s by Store.settings.collectAsStateWithLifecycle()

    LaunchedEffect(requestedTab) {
        requestedTab?.let { current = it; settings = false; onTabConsumed() }
    }

    val tab = remember(current) { tabs.firstOrNull { it.key == current } ?: tabs[0] }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text(if (settings) "IMPOSTAZIONI" else tab.title.uppercase(), style = MaterialTheme.typography.labelLarge) },
                    actions = {
                        IconButton(onClick = { settings = !settings }) {
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
                when {
                    settings -> SettingsScreen()
                    current == MainActivity.TAB_ALARM -> AlarmScreen()
                    current == MainActivity.TAB_CLOCK -> WorldClockScreen()
                    current == MainActivity.TAB_TIMER -> TimerScreen()
                    current == MainActivity.TAB_STOPWATCH -> StopwatchScreen()
                    else -> CalendarScreen()
                }
            }
        }
        NothingBottomBar(
            selected = if (settings) null else current,
            appearance = s.barAppearance,
            onSelect = { current = it; settings = false },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

/**
 * Pillola fluttuante in stile Nothing OS 5, non piu' una barra a tutta
 * larghezza: solida o "vetro" a seconda della scelta nelle impostazioni,
 * sempre con i colori del tema.
 */
@Composable
private fun NothingBottomBar(
    selected: String?,
    appearance: BarAppearance,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val background = if (appearance == BarAppearance.BLUR)
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.6f)
    else MaterialTheme.colorScheme.surfaceContainer

    Row(
        modifier
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(50))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 8.dp),
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

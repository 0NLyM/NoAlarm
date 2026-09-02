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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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

    LaunchedEffect(requestedTab) {
        requestedTab?.let { current = it; settings = false; onTabConsumed() }
    }

    val tab = remember(current) { tabs.firstOrNull { it.key == current } ?: tabs[0] }

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
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = !settings && t.key == current,
                        onClick = { current = t.key; settings = false },
                        icon = { Icon(t.icon, t.title) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onSecondary,
                            indicatorColor = MaterialTheme.colorScheme.secondary,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
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
}

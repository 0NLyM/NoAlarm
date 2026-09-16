package com.noalarm.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Schermata minima: conferma che l'app e' installata e in ascolto, con un test manuale. */
class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        BridgeService.start(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Senza il permesso l'eco (vedi BridgeService) non puo' notificare
        // affatto: va chiesto subito, niente altro nell'app lo fa comparire prima.
        if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Sotto la 31 il Bluetooth e' un permesso normale, gia' concesso
        // all'installazione: BridgeService puo' partire subito.
        if (Build.VERSION.SDK_INT < 31 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
            == PackageManager.PERMISSION_GRANTED
        ) {
            BridgeService.start(this)
        } else {
            requestBluetooth.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
        setContent {
            NoAlarmWatchTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            "NOALARM",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.secondary,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "In ascolto del telefono",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        PillButton(
                            "Prova",
                            onClick = { startActivity(RingActivity.testIntent(this@MainActivity)) },
                        )
                    }
                }
            }
        }
    }
}

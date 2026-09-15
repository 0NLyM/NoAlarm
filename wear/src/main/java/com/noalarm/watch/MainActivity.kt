package com.noalarm.watch

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Schermata minima: conferma che l'app e' installata e in ascolto, con un test manuale. */
class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val requestBluetooth = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        BridgeService.start(this)
    }
    // Da Android 14 dichiararlo nel manifest non basta piu': senza il consenso
    // esplicito dell'utente in Impostazioni, la notifica a schermo intero
    // dell'eco (BridgeService) degrada in silenzio a notifica normale.
    private val fullScreenIntentGranted = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Senza il permesso la notifica a schermo intero dell'eco (vedi
        // BridgeService) non compare affatto: va chiesto subito, niente
        // altro nell'app la fa comparire prima.
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
                        if (!fullScreenIntentGranted.value) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Manca il permesso per le notifiche a schermo intero: la sveglia " +
                                    "arriverebbe solo come notifica, non a tutto schermo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            PillButton("Concedi", onClick = ::requestFullScreenIntent)
                        }
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

    override fun onResume() {
        super.onResume()
        fullScreenIntentGranted.value = canUseFullScreenIntent()
    }

    private fun canUseFullScreenIntent(): Boolean =
        Build.VERSION.SDK_INT < 34 || getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    private fun requestFullScreenIntent() {
        runCatching {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        }
    }
}

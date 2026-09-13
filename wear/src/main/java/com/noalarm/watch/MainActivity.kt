package com.noalarm.watch

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Schermata minima: conferma che l'app e' installata e in ascolto, con un test manuale. */
class MainActivity : ComponentActivity() {
    private val requestNotifications = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Senza il permesso la notifica a schermo intero dell'eco (vedi
        // RingListenerService) non compare affatto: va chiesto subito,
        // niente altro nell'app la fa comparire prima.
        if (Build.VERSION.SDK_INT >= 33) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("NoAlarm")
                    Text("In ascolto del telefono", style = MaterialTheme.typography.bodySmall)
                    Button(onClick = { startActivity(RingActivity.testIntent(this@MainActivity)) }) {
                        Text("Prova")
                    }
                }
            }
        }
    }
}

package fr.arsenal.rallyetripmeter

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import fr.arsenal.rallyetripmeter.ui.screen.TripMeterRoute
import fr.arsenal.rallyetripmeter.ui.theme.RallyeTripMeterTheme

class MainActivity : ComponentActivity() {

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Résultat volontairement ignoré : l'application continue de fonctionner.
            // Si la permission est refusée, la notification du foreground service peut
            // ne pas être visible, mais l'accumulation (côté service) n'est pas affectée.
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()

        setContent {
            RallyeTripMeterTheme {
                TripMeterRoute()
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        // POST_NOTIFICATIONS est une permission runtime uniquement à partir d'Android 13.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        val alreadyGranted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

        if (!alreadyGranted) {
            requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

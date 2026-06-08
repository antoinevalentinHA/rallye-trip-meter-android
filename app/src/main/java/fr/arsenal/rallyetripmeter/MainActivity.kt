package fr.arsenal.rallyetripmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.arsenal.rallyetripmeter.ui.screen.TripMeterRoute
import fr.arsenal.rallyetripmeter.ui.theme.RallyeTripMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RallyeTripMeterTheme {
                TripMeterRoute()
            }
        }
    }
}

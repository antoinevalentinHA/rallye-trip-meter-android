package fr.arsenal.rallyetripmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState
import fr.arsenal.rallyetripmeter.ui.screen.TripMeterScreen
import fr.arsenal.rallyetripmeter.ui.theme.RallyeTripMeterTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            RallyeTripMeterTheme {
                TripMeterScreen(
                    state = bootstrapTripState().toTripDisplayState()
                )
            }
        }
    }
}

private fun bootstrapTripState(): TripState {
    return TripState(
        totalDistanceMeters = 124370.0,
        partialDistanceMeters = 800.0,
        gpsStatus = GpsStatus.Fixed,
        sessionState = TripSessionState.Running
    )
}

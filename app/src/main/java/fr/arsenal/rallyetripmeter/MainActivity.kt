package fr.arsenal.rallyetripmeter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
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
            val controller = remember { ImmutableTripController() }
            var tripState by remember { mutableStateOf(bootstrapTripState()) }

            RallyeTripMeterTheme {
                TripMeterScreen(
                    state = tripState.toTripDisplayState(),
                    onAdjustPartialMinus100 = {
                        tripState = controller.adjustPartial(
                            state = tripState,
                            deltaMeters = -100.0
                        )
                    },
                    onAdjustPartialMinus10 = {
                        tripState = controller.adjustPartial(
                            state = tripState,
                            deltaMeters = -10.0
                        )
                    },
                    onResetPartial = {
                        tripState = controller.resetPartial(tripState)
                    },
                    onAdjustPartialPlus10 = {
                        tripState = controller.adjustPartial(
                            state = tripState,
                            deltaMeters = 10.0
                        )
                    },
                    onAdjustPartialPlus100 = {
                        tripState = controller.adjustPartial(
                            state = tripState,
                            deltaMeters = 100.0
                        )
                    },
                    onSessionAction = {
                        tripState = when (tripState.sessionState) {
                            TripSessionState.Running -> controller.pause(tripState)
                            TripSessionState.Paused -> controller.resume(tripState)
                            TripSessionState.Stopped -> controller.start(tripState)
                        }
                    },
                    onStop = {
                        tripState = controller.stop(tripState)
                    },
                    onOptions = {
                        // v0.1:
                        // écran options non implémenté.
                    }
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

package fr.arsenal.rallyetripmeter.ui.screen

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState

/*
 * ARSENAL RALLYE — UI route
 *
 * Rôle :
 * - Héberge temporairement l'état Compose local du trip meter.
 * - Relie l'écran UI au contrôleur métier immutable.
 *
 * Contraintes :
 * - Aucun GPS réel.
 * - Aucun ViewModel.
 * - Aucune persistance.
 * - Aucun calcul de distance réelle.
 *
 * Statut :
 * - Palier transitoire avant extraction vers ViewModel.
 */
@Composable
fun TripMeterRoute() {
    val controller = remember { ImmutableTripController() }
    var tripState by remember { mutableStateOf(bootstrapTripState()) }

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

private fun bootstrapTripState(): TripState {
    return TripState(
        totalDistanceMeters = 124370.0,
        partialDistanceMeters = 800.0,
        gpsStatus = GpsStatus.Fixed,
        sessionState = TripSessionState.Running
    )
}

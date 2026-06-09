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
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent

/*
 * ARSENAL RALLYE — UI route
 *
 * Rôle :
 * - Héberge temporairement l'état Compose local du trip meter.
 * - Relie l'écran UI au contrôleur métier immutable.
 * - Route les événements UI vers les actions métier.
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
        onEvent = { event ->
            tripState = handleTripMeterUiEvent(
                event = event,
                state = tripState,
                controller = controller
            )
        }
    )
}

private fun handleTripMeterUiEvent(
    event: TripMeterUiEvent,
    state: TripState,
    controller: ImmutableTripController
): TripState {
    return when (event) {
        TripMeterUiEvent.AdjustPartialMinus100 -> controller.adjustPartial(
            state = state,
            deltaMeters = -100.0
        )

        TripMeterUiEvent.AdjustPartialMinus10 -> controller.adjustPartial(
            state = state,
            deltaMeters = -10.0
        )

        TripMeterUiEvent.ResetPartial -> controller.resetPartial(state)

        TripMeterUiEvent.AdjustPartialPlus10 -> controller.adjustPartial(
            state = state,
            deltaMeters = 10.0
        )

        TripMeterUiEvent.AdjustPartialPlus100 -> controller.adjustPartial(
            state = state,
            deltaMeters = 100.0
        )

        TripMeterUiEvent.SessionAction -> {
            when (state.sessionState) {
                TripSessionState.Running -> controller.pause(state)
                TripSessionState.Paused -> controller.resume(state)
                TripSessionState.Stopped -> controller.start(state)
            }
        }

        TripMeterUiEvent.Stop -> controller.stop(state)

        TripMeterUiEvent.Options -> state
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

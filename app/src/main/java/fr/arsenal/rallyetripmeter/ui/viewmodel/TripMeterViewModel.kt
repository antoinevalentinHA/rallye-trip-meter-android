package fr.arsenal.rallyetripmeter.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent

/*
 * ARSENAL RALLYE — Trip meter ViewModel
 *
 * Rôle :
 * - Héberge l'état UI du trip meter.
 * - Route les événements UI vers le contrôleur métier immutable.
 *
 * Contraintes :
 * - Aucun GPS réel.
 * - Aucun moteur de distance.
 * - Aucune persistance.
 * - Aucun effet de bord externe.
 *
 * Statut :
 * - Palier local avant intégration LocationEngine / DistanceEngine.
 */
class TripMeterViewModel : ViewModel() {
    private val controller = ImmutableTripController()

    private var tripState by mutableStateOf(bootstrapTripState())

    val uiState: TripDisplayState
        get() = tripState.toTripDisplayState()

    fun onEvent(event: TripMeterUiEvent) {
        tripState = handleTripMeterUiEvent(
            event = event,
            state = tripState
        )
    }

    private fun handleTripMeterUiEvent(
        event: TripMeterUiEvent,
        state: TripState
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
}

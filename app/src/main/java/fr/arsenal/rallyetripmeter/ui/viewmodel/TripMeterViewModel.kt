package fr.arsenal.rallyetripmeter.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.arsenal.rallyetripmeter.domain.permission.LocationPermissionState
import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.progress.DistanceTripProgressEngine
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState
import fr.arsenal.rallyetripmeter.ui.mapper.toUiLocationPermissionStatus
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent

/*
 * ARSENAL RALLYE — Trip meter ViewModel
 *
 * Rôle :
 * - Héberge l'état UI du trip meter.
 * - Route les événements UI vers le contrôleur métier immutable.
 * - Héberge un pas de progression simulé pour valider le pipeline distance.
 * - Expose un état de permission localisation affichable.
 *
 * Contraintes :
 * - Aucun GPS réel.
 * - Aucun Android Location.
 * - Aucune demande de permission runtime.
 * - Aucune persistance.
 * - Aucun effet de bord externe.
 *
 * Statut :
 * - Palier local avant intégration LocationEngine réel.
 */
class TripMeterViewModel(
    initialLocationPermissionState: LocationPermissionState = LocationPermissionState.Unknown
) : ViewModel() {
    private val controller = ImmutableTripController()

    private val progressEngine = DistanceTripProgressEngine(
        distanceEngine = HaversineDistanceEngine()
    )

    private var tripState by mutableStateOf(bootstrapTripState())

    private var locationPermissionState by mutableStateOf(initialLocationPermissionState)

    val uiState: TripDisplayState
        get() = tripState
            .toTripDisplayState()
            .copy(
                locationPermissionStatus = locationPermissionState.toUiLocationPermissionStatus()
            )

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

            TripMeterUiEvent.SimulateLocationStep -> progressEngine.applyLocationSample(
                state = state,
                previousSample = simulatedPreviousSample(),
                currentSample = simulatedCurrentSample()
            )
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

    private fun simulatedPreviousSample(): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792
            ),
            accuracyMeters = 4.0,
            speedMetersPerSecond = 12.0
        )
    }

    private fun simulatedCurrentSample(): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8380,
                longitude = -0.5794
            ),
            accuracyMeters = 4.0,
            speedMetersPerSecond = 12.0
        )
    }
}

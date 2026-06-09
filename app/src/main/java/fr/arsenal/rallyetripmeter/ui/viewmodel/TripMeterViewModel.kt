package fr.arsenal.rallyetripmeter.ui.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
import fr.arsenal.rallyetripmeter.domain.controller.TripController
import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.permission.LocationPermissionState
import fr.arsenal.rallyetripmeter.domain.progress.DistanceTripProgressEngine
import fr.arsenal.rallyetripmeter.domain.progress.TripProgressEngine
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
 * - Consomme un LocationEngine injecté pour le statut GPS et les échantillons de localisation.
 * - Route les échantillons de localisation vers le moteur de progression métier.
 *
 * Contraintes :
 * - Aucun GPS Android réel directement manipulé.
 * - Aucun Android Location exposé au domaine ou à l'UI.
 * - Aucune demande de permission runtime.
 * - Aucune persistance.
 * - Aucun effet de bord externe.
 *
 * Statut :
 * - Palier intermédiaire : LocationEngine injecté, adaptateur Android réel encore squelette.
 */
class TripMeterViewModel(
    initialLocationPermissionState: LocationPermissionState = LocationPermissionState.Unknown,
    private val readLocationPermissionState: () -> LocationPermissionState = {
        LocationPermissionState.Unknown
    },
    private val startLocationUpdates: () -> Unit = {},
    private val stopLocationUpdates: () -> Unit = {},
    private val controller: TripController = ImmutableTripController(),
    private val progressEngine: TripProgressEngine = DistanceTripProgressEngine(
        distanceEngine = HaversineDistanceEngine()
    ),
    private val locationEngine: LocationEngine = UnavailableLocationEngine(),
    initialTripState: TripState = bootstrapTripState(
        gpsStatus = locationEngine.getGpsStatus()
    )
) : ViewModel() {
    private var tripState by mutableStateOf(initialTripState)

    private var previousLocationSample: LocationSample? = null

    private var locationPermissionState by mutableStateOf(initialLocationPermissionState)

    val uiState: TripDisplayState
        get() = tripState
            .toTripDisplayState()
            .copy(
                locationPermissionStatus = locationPermissionState.toUiLocationPermissionStatus()
            )

    fun onStartLocation() {
        startLocationUpdates()
    }

    fun onStopLocation() {
        stopLocationUpdates()
    }

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

            TripMeterUiEvent.RefreshLocationPermission -> {
                locationPermissionState = readLocationPermissionState()
                state
            }
            TripMeterUiEvent.ApplyLocationSample -> applyLocationEngineSample(state)

            TripMeterUiEvent.SimulateLocationStep -> progressEngine.applyLocationSample(
                state = state,
                previousSample = simulatedPreviousLocationSample(),
                currentSample = simulatedCurrentLocationSample()
            )
        }
    }

    private fun applyLocationEngineSample(
        state: TripState
    ): TripState {
        val currentSample = locationEngine.getLastLocationSample()
            ?: return state

        val previousSample = previousLocationSample

        previousLocationSample = currentSample

        if (previousSample == null) {
            return state
        }

        return progressEngine.applyLocationSample(
            state = state,
            previousSample = previousSample,
            currentSample = currentSample
        )
    }

    private class UnavailableLocationEngine : LocationEngine {
        override fun getGpsStatus(): GpsStatus {
            return GpsStatus.Unavailable
        }

        override fun getLastLocationSample(): LocationSample? {
            return null
        }
    }

    private companion object {
        fun bootstrapTripState(
            gpsStatus: GpsStatus
        ): TripState {
            return TripState(
                totalDistanceMeters = 124370.0,
                partialDistanceMeters = 800.0,
                gpsStatus = gpsStatus,
                sessionState = TripSessionState.Running
            )
        }
    }
}

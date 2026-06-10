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
import fr.arsenal.rallyetripmeter.domain.persistence.NoOpTripStateStore
import fr.arsenal.rallyetripmeter.domain.persistence.PeriodicSaveThrottle
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateStore
import fr.arsenal.rallyetripmeter.domain.persistence.toTripStateSnapshot
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
 * - Persistance déléguée à un TripStateStore injecté (no-op par défaut).
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
    private val startForegroundService: () -> Unit = {},
    private val stopForegroundService: () -> Unit = {},
    private val controller: TripController = ImmutableTripController(),
    private val progressEngine: TripProgressEngine = DistanceTripProgressEngine(
        distanceEngine = HaversineDistanceEngine()
    ),
    private val locationEngine: LocationEngine = UnavailableLocationEngine(),
    private val tripStateStore: TripStateStore = NoOpTripStateStore(),
    private val periodicSaveThrottle: PeriodicSaveThrottle = PeriodicSaveThrottle(
        nowMillis = System::currentTimeMillis
    ),
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
        locationPermissionState = readLocationPermissionState()

        if (locationPermissionState == LocationPermissionState.Granted) {
            startLocationUpdates()
        }
    }

    fun onStopLocation() {
        stopLocationUpdates()
    }

    fun onEvent(event: TripMeterUiEvent) {
        val previousSessionState = tripState.sessionState

        tripState = handleTripMeterUiEvent(
            event = event,
            state = tripState
        )

        syncForegroundService(
            previousSessionState = previousSessionState,
            currentSessionState = tripState.sessionState
        )

        if (persistsOnEvent(event)) {
            persistTripState()
        } else if (event == TripMeterUiEvent.ApplyLocationSample) {
            persistPeriodically()
        }
    }

    private fun syncForegroundService(
        previousSessionState: TripSessionState,
        currentSessionState: TripSessionState
    ) {
        if (currentSessionState == previousSessionState) {
            return
        }

        when (currentSessionState) {
            TripSessionState.Running -> {
                if (locationPermissionState == LocationPermissionState.Granted) {
                    startForegroundService()
                }
            }

            TripSessionState.Stopped -> stopForegroundService()

            TripSessionState.Paused -> Unit
        }
    }

    fun persistCurrentState() {
        persistTripState()
    }

    private fun persistTripState() {
        tripStateStore.save(tripState.toTripStateSnapshot())
    }

    private fun persistPeriodically() {
        val snapshot = periodicSaveThrottle.pollSnapshotToSave(
            snapshot = tripState.toTripStateSnapshot()
        ) ?: return

        tripStateStore.save(snapshot)
    }

    private fun persistsOnEvent(event: TripMeterUiEvent): Boolean {
        return when (event) {
            TripMeterUiEvent.SessionAction,
            TripMeterUiEvent.Stop,
            TripMeterUiEvent.ResetPartial,
            TripMeterUiEvent.AdjustPartialPlus10,
            TripMeterUiEvent.AdjustPartialMinus10,
            TripMeterUiEvent.AdjustPartialPlus100,
            TripMeterUiEvent.AdjustPartialMinus100 -> true

            TripMeterUiEvent.Options,
            TripMeterUiEvent.RefreshLocationPermission,
            TripMeterUiEvent.ApplyLocationSample,
            TripMeterUiEvent.SimulateLocationStep -> false
        }
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

        val stateWithGpsStatus = state.copy(
            gpsStatus = locationEngine.getGpsStatus(),
            accuracyMeters = currentSample?.accuracyMeters,
            speedMetersPerSecond = currentSample?.speedMetersPerSecond
        )

        if (currentSample == null) {
            return stateWithGpsStatus
        }

        val previousSample = previousLocationSample

        previousLocationSample = currentSample

        if (previousSample == null) {
            return stateWithGpsStatus
        }

        return progressEngine.applyLocationSample(
            state = stateWithGpsStatus,
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
                totalDistanceMeters = 0.0,
                partialDistanceMeters = 0.0,
                gpsStatus = gpsStatus,
                sessionState = TripSessionState.Stopped
            )
        }
    }
}

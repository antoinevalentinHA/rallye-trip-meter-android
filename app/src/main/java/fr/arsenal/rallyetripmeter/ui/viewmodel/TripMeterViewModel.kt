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
import fr.arsenal.rallyetripmeter.domain.progress.DistanceTripProgressEngine
import fr.arsenal.rallyetripmeter.domain.progress.TripProgressEngine
import fr.arsenal.rallyetripmeter.runtime.TripRuntime
import fr.arsenal.rallyetripmeter.ui.mapper.toTripDisplayState
import fr.arsenal.rallyetripmeter.ui.mapper.toUiLocationPermissionStatus
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent

/*
 * ARSENAL RALLYE — Trip meter ViewModel
 *
 * Rôle :
 * - Adaptateur UI du trip meter : projette l'état autoritaire du TripRuntime.
 * - Délègue les événements au TripRuntime (autorité métier + persistance).
 * - Conserve la permission de localisation et le pilotage du foreground service.
 * - Tient un miroir Compose de l'état runtime pour la recomposition.
 *
 * Contraintes :
 * - Aucun GPS Android réel directement manipulé.
 * - Aucun Android Location exposé au domaine ou à l'UI.
 * - Aucune demande de permission runtime.
 * - Persistance déléguée au TripRuntime (TripStateStore no-op par défaut).
 * - Aucun effet de bord externe.
 *
 * Statut :
 * - B1 : TripRuntime extrait, encore possédé par le ViewModel (per-VM, non process-wide).
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
    ),
    private val runtime: TripRuntime = TripRuntime(
        controller = controller,
        progressEngine = progressEngine,
        locationEngine = locationEngine,
        tripStateStore = tripStateStore,
        periodicSaveThrottle = periodicSaveThrottle,
        initialState = initialTripState
    )
) : ViewModel() {
    private var stateMirror by mutableStateOf(runtime.state)

    private var locationPermissionState by mutableStateOf(initialLocationPermissionState)

    val uiState: TripDisplayState
        get() = stateMirror
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
        if (event == TripMeterUiEvent.RefreshLocationPermission) {
            locationPermissionState = readLocationPermissionState()
        }

        val previousSessionState = runtime.state.sessionState

        runtime.onEvent(event)

        stateMirror = runtime.state

        syncForegroundService(
            previousSessionState = previousSessionState,
            currentSessionState = runtime.state.sessionState
        )
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

            TripSessionState.Paused -> stopForegroundService()
        }
    }

    fun persistCurrentState() {
        runtime.persistCurrentState()
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

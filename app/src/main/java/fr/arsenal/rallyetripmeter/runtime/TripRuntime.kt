package fr.arsenal.rallyetripmeter.runtime

import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
import fr.arsenal.rallyetripmeter.domain.controller.TripController
import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.persistence.NoOpTripStateStore
import fr.arsenal.rallyetripmeter.domain.persistence.PeriodicSaveThrottle
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateStore
import fr.arsenal.rallyetripmeter.domain.persistence.toTripStateSnapshot
import fr.arsenal.rallyetripmeter.domain.progress.DistanceTripProgressEngine
import fr.arsenal.rallyetripmeter.domain.progress.TripProgressEngine
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent

/*
 * ARSENAL RALLYE — Trip runtime
 *
 * Rôle :
 * - Autorité unique de l'état métier du trip meter (TripState).
 * - Route les événements métier vers le contrôleur immutable.
 * - Consomme un LocationEngine injecté pour le statut GPS et les échantillons.
 * - Route les échantillons vers le moteur de progression métier.
 * - Décide et applique la persistance (simple + throttle périodique).
 *
 * Contraintes :
 * - Pur Kotlin : aucun Android, aucune coroutine, aucun singleton.
 * - Aucune dépendance vers ui.viewmodel.
 * - Aucune permission runtime (concern du ViewModel).
 * - Aucun pilotage du foreground service (concern du ViewModel).
 * - Persistance déléguée à un TripStateStore injecté (no-op par défaut).
 *
 * Statut :
 * - B1 : runtime encore possédé par le ViewModel (per-VM, non process-wide),
 *   accepte encore TripMeterUiEvent.
 */
class TripRuntime(
    private val controller: TripController = ImmutableTripController(),
    private val progressEngine: TripProgressEngine = DistanceTripProgressEngine(
        distanceEngine = HaversineDistanceEngine()
    ),
    private val locationEngine: LocationEngine = UnavailableLocationEngine(),
    private val tripStateStore: TripStateStore = NoOpTripStateStore(),
    private val periodicSaveThrottle: PeriodicSaveThrottle = PeriodicSaveThrottle(
        nowMillis = System::currentTimeMillis
    ),
    initialState: TripState = TripState()
) {
    var state: TripState = initialState
        private set

    private var previousLocationSample: LocationSample? = null

    fun onEvent(event: TripMeterUiEvent) {
        state = handleTripMeterUiEvent(
            event = event,
            state = state
        )

        if (persistsOnEvent(event)) {
            persistTripState()
        } else if (event == TripMeterUiEvent.ApplyLocationSample) {
            persistPeriodically()
        }
    }

    fun persistCurrentState() {
        persistTripState()
    }

    private fun persistTripState() {
        tripStateStore.save(state.toTripStateSnapshot())
    }

    private fun persistPeriodically() {
        val snapshot = periodicSaveThrottle.pollSnapshotToSave(
            snapshot = state.toTripStateSnapshot()
        ) ?: return

        tripStateStore.save(snapshot)
    }

    private fun persistsOnEvent(event: TripMeterUiEvent): Boolean {
        return when (event) {
            TripMeterUiEvent.SessionAction,
            TripMeterUiEvent.Stop,
            TripMeterUiEvent.ResetTotal,
            TripMeterUiEvent.NewRun,
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

            TripMeterUiEvent.ResetTotal -> controller.resetTotal(state)

            TripMeterUiEvent.NewRun -> controller.resetTrip(state)

            TripMeterUiEvent.Options -> state

            TripMeterUiEvent.RefreshLocationPermission -> state

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

    /*
     * Échantillons simulés transitoires (support de simulation avant branchement
     * d'une source LocationEngine réelle). Internalisés ici pour éviter toute
     * dépendance runtime -> ui.viewmodel.
     */
    private fun simulatedPreviousLocationSample(): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792
            ),
            accuracyMeters = 4.0,
            speedMetersPerSecond = 12.0
        )
    }

    private fun simulatedCurrentLocationSample(): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8380,
                longitude = -0.5794
            ),
            accuracyMeters = 4.0,
            speedMetersPerSecond = 12.0
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
}

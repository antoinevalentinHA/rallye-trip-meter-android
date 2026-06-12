package fr.arsenal.rallyetripmeter.runtime

import fr.arsenal.rallyetripmeter.domain.controller.ImmutableTripController
import fr.arsenal.rallyetripmeter.domain.controller.TripController
import fr.arsenal.rallyetripmeter.domain.diag.NoOpTickLogSink
import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink
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
import fr.arsenal.rallyetripmeter.domain.progress.FilterState
import fr.arsenal.rallyetripmeter.domain.progress.GpsAccumulationFilter
import fr.arsenal.rallyetripmeter.domain.progress.MachineState

/*
 * ARSENAL RALLYE — Trip runtime
 *
 * Rôle :
 * - Autorité unique de l'état métier du trip meter (TripState).
 * - Route les événements métier vers le contrôleur immutable.
 * - Consomme un LocationEngine injecté pour le statut GPS et les échantillons.
 * - Route les échantillons vers le filtre d'accumulation GPS, qui détient
 *   l'ancre via un FilterState transporté de façon opaque.
 * - Décide et applique la persistance (simple + throttle périodique).
 * - Émet une entrée d'observabilité par tick ApplyLocationSample (P1.c),
 *   via un TickLogSink injecté (no-op par défaut).
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
 *   accepte un TripRuntimeEvent pur (traduit par le ViewModel).
 */
class TripRuntime(
    private val controller: TripController = ImmutableTripController(),
    private val gpsAccumulationFilter: GpsAccumulationFilter = DistanceTripProgressEngine(
        distanceEngine = HaversineDistanceEngine()
    ),
    private val locationEngine: LocationEngine = UnavailableLocationEngine(),
    private val tripStateStore: TripStateStore = NoOpTripStateStore(),
    private val periodicSaveThrottle: PeriodicSaveThrottle = PeriodicSaveThrottle(
        nowMillis = System::currentTimeMillis
    ),
    private val tickLogSink: TickLogSink = NoOpTickLogSink(),
    private val nowMillis: () -> Long = System::currentTimeMillis,
    initialState: TripState = TripState()
) {
    var state: TripState = initialState
        private set

    private var filterState: FilterState = FilterState()

    fun onEvent(event: TripRuntimeEvent) {
        state = handleTripRuntimeEvent(
            event = event,
            state = state
        )

        if (persistsOnEvent(event)) {
            persistTripState()
        } else if (event == TripRuntimeEvent.ApplyLocationSample) {
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

    private fun persistsOnEvent(event: TripRuntimeEvent): Boolean {
        return when (event) {
            TripRuntimeEvent.SessionAction,
            TripRuntimeEvent.Stop,
            TripRuntimeEvent.ResetTotal,
            TripRuntimeEvent.NewRun,
            TripRuntimeEvent.ResetPartial,
            TripRuntimeEvent.AdjustPartialPlus10,
            TripRuntimeEvent.AdjustPartialMinus10,
            TripRuntimeEvent.AdjustPartialPlus100,
            TripRuntimeEvent.AdjustPartialMinus100 -> true

            TripRuntimeEvent.ApplyLocationSample,
            TripRuntimeEvent.SimulateLocationStep -> false
        }
    }

    private fun handleTripRuntimeEvent(
        event: TripRuntimeEvent,
        state: TripState
    ): TripState {
        return when (event) {
            TripRuntimeEvent.AdjustPartialMinus100 -> controller.adjustPartial(
                state = state,
                deltaMeters = -100.0
            )

            TripRuntimeEvent.AdjustPartialMinus10 -> controller.adjustPartial(
                state = state,
                deltaMeters = -10.0
            )

            TripRuntimeEvent.ResetPartial -> controller.resetPartial(state)

            TripRuntimeEvent.AdjustPartialPlus10 -> controller.adjustPartial(
                state = state,
                deltaMeters = 10.0
            )

            TripRuntimeEvent.AdjustPartialPlus100 -> controller.adjustPartial(
                state = state,
                deltaMeters = 100.0
            )

            TripRuntimeEvent.SessionAction -> {
                when (state.sessionState) {
                    TripSessionState.Running -> controller.pause(state)
                    TripSessionState.Paused -> controller.resume(state)
                    TripSessionState.Stopped -> controller.start(state)
                }
            }

            TripRuntimeEvent.Stop -> controller.stop(state)

            TripRuntimeEvent.ResetTotal -> controller.resetTotal(state)

            TripRuntimeEvent.NewRun -> controller.resetTrip(state)

            TripRuntimeEvent.ApplyLocationSample -> applyLocationEngineSample(state)

            TripRuntimeEvent.SimulateLocationStep -> gpsAccumulationFilter.apply(
                tripState = state,
                // Un pas simulé est, par intention, un DÉPLACEMENT injecté pour
                // tester le compteur. L'état transitoire est donc déclaré MOVING :
                // sinon le gate stationnaire (P4.2) neutraliserait le pas (l'état
                // transitoire par défaut serait STATIONARY). L'état filtre PERSISTANT
                // n'est pas touché (transport opaque préservé).
                filterState = FilterState(
                    anchor = simulatedPreviousLocationSample(),
                    machineState = MachineState.MOVING,
                    stationaryCenter = simulatedPreviousLocationSample()
                ),
                currentSample = simulatedCurrentLocationSample()
            ).state
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
            logTick(
                state = stateWithGpsStatus,
                currentSample = null,
                previousSample = filterState.anchor,
                sampleIsNew = null,
                verdict = SampleVerdict.IGNORED_NO_SAMPLE,
                deltaTotalMeters = 0.0
            )
            return stateWithGpsStatus
        }

        val previousSample = filterState.anchor

        // Relecture du cache sans nouveau fix : verdict de niveau runtime
        // (IGNORED_DUPLICATE, jamais produit par le filtre). L'état filtre reste
        // intact : l'ancre n'avance pas. Comportement inchangé.
        if (currentSample == previousSample) {
            logTick(
                state = stateWithGpsStatus,
                currentSample = currentSample,
                previousSample = previousSample,
                sampleIsNew = false,
                verdict = SampleVerdict.IGNORED_DUPLICATE,
                deltaTotalMeters = 0.0
            )
            return stateWithGpsStatus
        }

        // Le filtre détient désormais l'ancre : il produit le verdict, le nouvel
        // état métier et l'état filtre suivant. Le runtime lit l'ancre pour la
        // déduplication de cache ci-dessus, mais ne l'avance jamais lui-même :
        // seul result.nextState fait avancer l'ancre — y compris sur rejet
        // (sémantique héritée conservée, transport opaque).
        val result = gpsAccumulationFilter.apply(
            tripState = stateWithGpsStatus,
            filterState = filterState,
            currentSample = currentSample
        )
        filterState = result.nextState

        logTick(
            state = result.state,
            currentSample = currentSample,
            previousSample = previousSample,
            sampleIsNew = true,
            verdict = result.verdict,
            deltaTotalMeters = result.state.totalDistanceMeters -
                stateWithGpsStatus.totalDistanceMeters
        )

        return result.state
    }

    /*
     * Émission d'observabilité (P1.c) : une entrée par tick ApplyLocationSample.
     * Les champs internes au moteur (segment, plancher, vitesse implicite)
     * restent null à ce palier : les calculer ici dupliquerait la logique.
     */
    private fun logTick(
        state: TripState,
        currentSample: LocationSample?,
        previousSample: LocationSample?,
        sampleIsNew: Boolean?,
        verdict: SampleVerdict,
        deltaTotalMeters: Double
    ) {
        tickLogSink.log(
            TickLogEntry(
                tickElapsedMillis = nowMillis(),
                sampleTimestampMillis = currentSample?.point?.timestampMillis,
                sampleIsNew = sampleIsNew,
                latitude = currentSample?.point?.latitude,
                longitude = currentSample?.point?.longitude,
                accuracyMeters = currentSample?.accuracyMeters,
                speedMetersPerSecond = currentSample?.speedMetersPerSecond,
                gpsStatus = state.gpsStatus,
                sessionState = state.sessionState,
                previousTimestampMillis = previousSample?.point?.timestampMillis,
                segmentMeters = null,
                verdict = verdict,
                floorMeters = null,
                impliedSpeedKmh = null,
                deltaTotalMeters = deltaTotalMeters,
                totalMeters = state.totalDistanceMeters
            )
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

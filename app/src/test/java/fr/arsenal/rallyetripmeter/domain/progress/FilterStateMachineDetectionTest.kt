package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * ARSENAL RALLYE — Gate stationnaire actif (P4.2)
 *
 * Couvre :
 * - initialisation de la machine (STATIONARY, centre posé) ;
 * - gate STATIONARY : la dérive n'est pas accumulée, l'ancre reste gelée ;
 * - transition STATIONARY -> MOVING (hystérésis), crédit du segment de départ ;
 * - non-régression MOVING : miroir exact de la primitive (P4.1 préservé) ;
 * - transition MOVING -> STATIONARY après immobilité soutenue.
 *
 * Seuils par défaut P4.2 : movementTriggerMeters=15, stillnessRadiusMeters=1,
 * detectionHysteresisSamples=8.
 */
class FilterStateMachineDetectionTest {

    private val metersPerDegreeLat = 111_320.0

    // --- Initialisation ---

    @Test
    fun firstApply_isStationary_andCentersOnFirstSample() {
        val first = sampleAt(0L)
        val next = engine().apply(runningState(), FilterState(), first).nextState

        assertEquals(MachineState.STATIONARY, next.machineState)
        assertEquals(first, next.stationaryCenter)
        assertEquals(first, next.anchor)
    }

    // --- Gate STATIONARY : dérive neutralisée, ancre gelée ---

    @Test
    fun stationaryDrift_isNotAccumulated_andAnchorStaysFrozen() {
        val center = sampleAt(0L)
        // Dérive à 8 m du centre, vitesse parasite 8 m/s : P4.a l'aurait acceptée.
        val drift = sampleNorthMeters(8.0, 1_000L)

        val result = engine().apply(
            runningState(),
            FilterState(anchor = center, machineState = MachineState.STATIONARY, stationaryCenter = center),
            drift
        )

        assertEquals(runningState(), result.state) // aucune accumulation
        assertEquals(center, result.nextState.anchor) // ancre gelée au centre
        assertEquals(MachineState.STATIONARY, result.nextState.machineState)
    }

    // --- Transition STATIONARY -> MOVING (hystérésis = 8) ---

    @Test
    fun sustainedDeparture_transitionsToMoving_afterHysteresis_andCreditsDeparture() {
        val center = sampleAt(0L)
        val far = sampleNorthMeters(20.0, 1_000L) // > movementTriggerMeters (15)

        // 7 échantillons éloignés : encore STATIONARY, rien accumulé.
        val afterSeven = chain(
            runningState(),
            FilterState(anchor = center, machineState = MachineState.STATIONARY, stationaryCenter = center),
            List(7) { far }
        )
        assertEquals(MachineState.STATIONARY, afterSeven.nextState.machineState)
        assertEquals(runningState().totalDistanceMeters, afterSeven.state.totalDistanceMeters, 0.0)

        // 8e échantillon consécutif : bascule MOVING + crédit du segment de départ.
        val atTransition = engine().apply(runningState(), afterSeven.nextState, far)
        assertEquals(MachineState.MOVING, atTransition.nextState.machineState)
        assertEquals(far, atTransition.nextState.anchor)
        assertTrue(
            "Le départ doit créditer le segment centre->courant.",
            atTransition.state.totalDistanceMeters > runningState().totalDistanceMeters
        )
    }

    // --- Non-régression MOVING : miroir exact de la primitive ---

    @Test
    fun movingState_mirrorsPrimitive_andAdvancesAnchor() {
        val anchor = sampleAt(0L)
        val moved = sampleNorthMeters(20.0, 1_000L)

        val reference = engine().applyLocationSampleWithVerdict(runningState(), anchor, moved)
        val result = engine().apply(
            runningState(),
            FilterState(anchor = anchor, machineState = MachineState.MOVING, stationaryCenter = anchor),
            moved
        )

        assertEquals(reference.verdict, result.verdict)
        assertEquals(reference.state, result.state)
        assertEquals(moved, result.nextState.anchor)
    }

    // --- Transition MOVING -> STATIONARY après immobilité ---

    @Test
    fun sustainedStillness_whileMoving_transitionsBackToStationary() {
        val anchor = sampleNorthMeters(20.0, 0L)
        // 8 échantillons immobiles consécutifs (pas < stillnessRadius = 1 m, vitesse nulle).
        val stilled = chain(
            runningState(),
            FilterState(anchor = anchor, machineState = MachineState.MOVING, stationaryCenter = anchor),
            (1..8).map { sampleNorthMeters(20.0, it * 1_000L, speed = 0.0) }
        )

        assertEquals(MachineState.STATIONARY, stilled.nextState.machineState)
        assertEquals(0, stilled.nextState.stationaryStreak)
    }

    // --- Support ---

    private fun chain(
        initialState: TripState,
        initialFilter: FilterState,
        samples: List<LocationSample>
    ): FilterResult {
        var state = initialState
        var filter = initialFilter
        var last: FilterResult? = null
        for (sample in samples) {
            last = engine().apply(state, filter, sample)
            state = last.state
            filter = last.nextState
        }
        return last!!
    }

    private fun engine(): DistanceTripProgressEngine {
        return DistanceTripProgressEngine(distanceEngine = HaversineDistanceEngine())
    }

    private fun runningState(): TripState {
        return TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Running
        )
    }

    private fun sampleAt(timestampMillis: Long, speed: Double? = null): LocationSample {
        return LocationSample(
            point = GeoPoint(latitude = 44.8378, longitude = -0.5792, timestampMillis = timestampMillis),
            speedMetersPerSecond = speed
        )
    }

    private fun sampleNorthMeters(
        meters: Double,
        timestampMillis: Long,
        speed: Double? = 8.0
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378 + meters / metersPerDegreeLat,
                longitude = -0.5792,
                timestampMillis = timestampMillis
            ),
            speedMetersPerSecond = speed
        )
    }
}

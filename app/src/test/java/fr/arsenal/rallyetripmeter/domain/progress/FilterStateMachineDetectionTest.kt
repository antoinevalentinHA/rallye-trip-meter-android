package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * ARSENAL RALLYE — Neutral stationary/movement detection (P4.1)
 *
 * Objet :
 * - Prouver que l'état machine est initialisé, calculé (transitions avec
 *   hystérésis) et transporté par FilterState.
 * - Prouver la NEUTRALITÉ : l'état machine ne change ni le verdict, ni l'état
 *   métier, ni l'avancement d'ancre. L'accumulation reste celle de P4.a.
 *
 * Le détecteur utilise les valeurs par défaut de FilterTuning
 * (movementTriggerMeters=15, stillnessRadiusMeters=3, detectionHysteresisSamples=3).
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

    // --- Transitions avec hystérésis ---

    @Test
    fun sustainedDeparture_transitionsToMoving_afterHysteresis() {
        val center = sampleAt(0L)
        val far = sampleNorthMeters(20.0, 1_000L) // > movementTriggerMeters (15)

        // Après 2 échantillons éloignés : pas encore basculé (hystérésis = 3).
        val afterTwo = feed(FilterState(), listOf(center, far, far))
        assertEquals(MachineState.STATIONARY, afterTwo.machineState)
        assertEquals(2, afterTwo.movingStreak)

        // Au 3e échantillon éloigné consécutif : bascule en MOVING.
        val afterThree = engine().apply(runningState(), afterTwo, far).nextState
        assertEquals(MachineState.MOVING, afterThree.machineState)
        assertEquals(0, afterThree.movingStreak)
    }

    @Test
    fun sustainedStillness_whileMoving_transitionsBackToStationary() {
        val center = sampleAt(0L)
        val far = sampleNorthMeters(20.0, 1_000L)

        // Amener la machine en MOVING.
        val moving = feed(FilterState(), listOf(center, far, far, far))
        assertEquals(MachineState.MOVING, moving.machineState)

        // 3 pas immobiles consécutifs (déplacement nul < stillnessRadius) -> STATIONARY.
        val stilled = feed(moving, listOf(far, far, far))
        assertEquals(MachineState.STATIONARY, stilled.machineState)
        assertEquals(0, stilled.stationaryStreak)
    }

    @Test
    fun belowTrigger_staysStationary_andResetsStreak() {
        val center = sampleAt(0L)
        val near = sampleNorthMeters(5.0, 1_000L) // < movementTriggerMeters (15)

        val next = feed(FilterState(), listOf(center, near, near, near))
        assertEquals(MachineState.STATIONARY, next.machineState)
        assertEquals(0, next.movingStreak)
    }

    // --- Neutralité : la détection ne touche pas l'accumulation ---

    @Test
    fun machineDetection_doesNotAffectVerdictOrState() {
        val anchor = sampleAt(0L)
        val current = sampleNorthMeters(20.0, 1_000L)

        // Même ancre, mais états machine différents en entrée.
        val asStationary = FilterState(anchor = anchor, machineState = MachineState.STATIONARY)
        val asMoving = FilterState(
            anchor = anchor,
            machineState = MachineState.MOVING,
            stationaryCenter = current,
            movingStreak = 2,
            stationaryStreak = 1
        )

        val r1 = engine().apply(runningState(), asStationary, current)
        val r2 = engine().apply(runningState(), asMoving, current)

        // Verdict et état métier strictement identiques : l'accumulation ne
        // dépend que de l'ancre, pas de l'état machine.
        assertEquals(r1.verdict, r2.verdict)
        assertEquals(r1.state, r2.state)
        // L'ancre avance dans les deux cas (comportement P4.a conservé).
        assertEquals(current, r1.nextState.anchor)
        assertEquals(current, r2.nextState.anchor)
    }

    @Test
    fun anchorStillAdvancesOnRejectedSample_unchangedFromP4a() {
        // Échantillon quasi immobile (vitesse 0.2) -> REJECTED_STATIONARY.
        val anchor = sampleAt(0L)
        val rejected = sampleAt(1_000L, speed = 0.2)

        val result = engine().apply(
            runningState(),
            FilterState(anchor = anchor),
            rejected
        )

        assertEquals(SampleVerdict.REJECTED_STATIONARY, result.verdict)
        // P4.1 ne corrige pas le bug d'ancre : elle avance toujours sur rejet.
        assertEquals(rejected, result.nextState.anchor)
    }

    // --- Support ---

    private fun feed(initial: FilterState, samples: List<LocationSample>): FilterState {
        var state = initial
        for (sample in samples) {
            state = engine().apply(runningState(), state, sample).nextState
        }
        return state
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

    private fun sampleAt(
        timestampMillis: Long,
        speed: Double? = null
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792,
                timestampMillis = timestampMillis
            ),
            speedMetersPerSecond = speed
        )
    }

    private fun sampleNorthMeters(
        meters: Double,
        timestampMillis: Long
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378 + meters / metersPerDegreeLat,
                longitude = -0.5792,
                timestampMillis = timestampMillis
            ),
            speedMetersPerSecond = 8.0
        )
    }
}

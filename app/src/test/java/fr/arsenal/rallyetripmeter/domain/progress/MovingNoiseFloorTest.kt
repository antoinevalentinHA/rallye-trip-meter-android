package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * ARSENAL RALLYE — Plancher de bruit réduit en MOVING (P5.c-3 étape A)
 *
 * Prouve :
 * - en état MOVING, un segment entre movingNoiseFloorMeters (1.4) et
 *   noiseFloorMeters (2.0), vitesse présente, est ACCEPTÉ (petit pas piéton sauvé) ;
 * - en MOVING, un segment sous movingNoiseFloorMeters reste REJECTED_NOISE ;
 * - hors MOVING (primitive sans état = crédit de départ / branche historique), le
 *   même segment 1.6 m reste REJECTED_NOISE (plancher 2.0 préservé, gate non affaibli).
 *
 * Le plancher réduit est donc strictement confiné au mouvement confirmé.
 */
class MovingNoiseFloorTest {

    private val metersPerDegreeLat = 111_320.0

    @Test
    fun movingState_acceptsSegmentBetweenMovingFloorAndNoiseFloor() {
        val anchor = sampleNorthMeters(0.0, 0L)
        val moved = sampleNorthMeters(1.6, 1_000L) // 1.4 < 1.6 < 2.0

        val result = engine().apply(
            runningState(),
            FilterState(anchor = anchor, machineState = MachineState.MOVING, stationaryCenter = anchor),
            moved
        )

        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, result.verdict)
        assertEquals(MachineState.MOVING, result.nextState.machineState)
        // La distance est bien accumulée (~1.6 m).
        assertTrue(result.state.totalDistanceMeters > 100.0)
    }

    @Test
    fun movingState_stillRejectsBelowMovingFloor() {
        val anchor = sampleNorthMeters(0.0, 0L)
        val moved = sampleNorthMeters(1.2, 1_000L) // < movingNoiseFloorMeters (1.4)

        val result = engine().apply(
            runningState(),
            FilterState(anchor = anchor, machineState = MachineState.MOVING, stationaryCenter = anchor),
            moved
        )

        assertEquals(SampleVerdict.REJECTED_NOISE, result.verdict)
    }

    @Test
    fun statelessPrimitive_keepsHistoricalNoiseFloor_rejectsSameSegment() {
        val anchor = sampleNorthMeters(0.0, 0L)
        val moved = sampleNorthMeters(1.6, 1_000L) // 1.6 < noiseFloorMeters (2.0)

        // Primitive sans état (moving = false) : exactement ce qu'utilisent le crédit
        // de départ STATIONARY -> MOVING et la branche historique. Plancher 2.0 conservé.
        val result = engine().applyLocationSampleWithVerdict(runningState(), anchor, moved)

        assertEquals(SampleVerdict.REJECTED_NOISE, result.verdict)
    }

    // --- Support ---

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

    private fun sampleNorthMeters(
        meters: Double,
        timestampMillis: Long,
        speed: Double? = 1.2
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

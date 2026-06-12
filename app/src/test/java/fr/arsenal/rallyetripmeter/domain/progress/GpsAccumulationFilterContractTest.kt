package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.distance.DistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * ARSENAL RALLYE — GpsAccumulationFilter contract test (P3.a → P4.2)
 *
 * Objet :
 * - En MOVING, `apply()` reproduit à l'identique la sémantique du moteur
 *   historique (`applyLocationSampleWithVerdict`) : mêmes verdicts, même état
 *   métier, et l'ancre avance vers l'échantillon courant à chaque application
 *   (comportement P3/P4.1 préservé, y compris sur rejet).
 * - En STATIONARY (P4.2), le gate est actif : la dérive n'est pas accumulée et
 *   l'ancre reste gelée au centre. Les sentinelles d'ancre « avance toujours »,
 *   conservées jusqu'en P4, sont ici inversées pour l'état STATIONARY.
 */
class GpsAccumulationFilterContractTest {

    // --- Parité verdict + état métier avec le moteur historique, par branche ---

    @Test
    fun apply_withoutAnchor_mirrorsIgnoredNoAnchor() {
        assertMirrorsEngine(
            engine = engineWithDistance(12.5),
            anchor = null,
            current = sampleAt(0L),
            state = runningState(),
            expectedVerdict = SampleVerdict.IGNORED_NO_ANCHOR
        )
    }

    @Test
    fun apply_whenStopped_mirrorsIgnoredNotRunning() {
        assertMirrorsEngine(
            engine = engineWithDistance(12.5),
            anchor = sampleAt(0L),
            current = sampleAt(1_000L),
            state = runningState().copy(sessionState = TripSessionState.Stopped),
            expectedVerdict = SampleVerdict.IGNORED_NOT_RUNNING
        )
    }

    @Test
    fun apply_whenStationarySpeed_mirrorsRejectedStationary() {
        assertMirrorsEngine(
            engine = engineWithDistance(12.0),
            anchor = sampleAt(0L, speedMetersPerSecond = 0.3),
            current = sampleAt(1_000L, speedMetersPerSecond = 0.3),
            state = runningState(),
            expectedVerdict = SampleVerdict.REJECTED_STATIONARY
        )
    }

    @Test
    fun apply_whenBelowNoiseFloor_mirrorsRejectedNoise() {
        assertMirrorsEngine(
            engine = engineWithDistance(1.5),
            anchor = sampleAt(0L),
            current = sampleAt(1_000L),
            state = runningState(),
            expectedVerdict = SampleVerdict.REJECTED_NOISE
        )
    }

    @Test
    fun apply_whenImplausibleJump_mirrorsRejectedImplausibleJump() {
        assertMirrorsEngine(
            engine = engineWithDistance(100.0),
            anchor = sampleAt(0L),
            current = sampleAt(1_000L),
            state = runningState(),
            expectedVerdict = SampleVerdict.REJECTED_IMPLAUSIBLE_JUMP
        )
    }

    @Test
    fun apply_whenPlausibleMovement_mirrorsAcceptedSegment() {
        assertMirrorsEngine(
            engine = engineWithDistance(12.5),
            anchor = sampleAt(0L),
            current = sampleAt(1_000L),
            state = runningState(),
            expectedVerdict = SampleVerdict.ACCEPTED_SEGMENT
        )
    }

    // --- Sémantique d'ancre : MOVING avance (P4.1), STATIONARY gèle (P4.2 gate) ---

    @Test
    fun movingState_advancesAnchorEvenWhenSampleRejected() {
        val current = sampleAt(1_000L)

        // En MOVING, le rejet ne fige pas l'ancre : elle avance (P3/P4.1 préservé).
        val rejectingCases = listOf(
            engineWithDistance(1.5) to sampleAt(0L), // REJECTED_NOISE
            engineWithDistance(100.0) to sampleAt(0L), // REJECTED_IMPLAUSIBLE_JUMP
        )

        rejectingCases.forEach { (engine, anchor) ->
            val result = engine.apply(
                tripState = runningState(),
                filterState = FilterState(
                    anchor = anchor,
                    machineState = MachineState.MOVING,
                    stationaryCenter = anchor
                ),
                currentSample = current
            )
            assertEquals(runningState(), result.state)       // état inchangé (rejet)
            assertEquals(current, result.nextState.anchor)    // …mais l'ancre a avancé
        }

        // Vitesse quasi nulle en MOVING (REJECTED_STATIONARY) : même règle.
        val stationaryCurrent = sampleAt(1_000L, speedMetersPerSecond = 0.3)
        val stationaryResult = engineWithDistance(12.0).apply(
            tripState = runningState(),
            filterState = FilterState(
                anchor = sampleAt(0L, speedMetersPerSecond = 0.3),
                machineState = MachineState.MOVING,
                stationaryCenter = sampleAt(0L, speedMetersPerSecond = 0.3)
            ),
            currentSample = stationaryCurrent
        )
        assertEquals(SampleVerdict.REJECTED_STATIONARY, stationaryResult.verdict)
        assertEquals(runningState(), stationaryResult.state)
        assertEquals(stationaryCurrent, stationaryResult.nextState.anchor)
    }

    @Test
    fun stationaryState_gatesDriftAndFreezesAnchor() {
        // P4.2 : en STATIONARY, un sample de dérive (que P4.a aurait pu accepter)
        // est neutralisé — état inchangé, verdict REJECTED_STATIONARY, et l'ancre
        // reste GELÉE au centre (elle ne suit pas la dérive : c'est ce qui évite
        // l'effet pervers de P4.b anchor-only).
        val center = sampleAt(0L)
        val drift = sampleAt(1_000L)
        val result = engineWithDistance(8.0).apply( // 8 m < movementTrigger (15)
            tripState = runningState(),
            filterState = FilterState(
                anchor = center,
                machineState = MachineState.STATIONARY,
                stationaryCenter = center
            ),
            currentSample = drift
        )

        assertEquals(SampleVerdict.REJECTED_STATIONARY, result.verdict)
        assertEquals(runningState(), result.state) // aucune accumulation
        assertEquals(center, result.nextState.anchor) // ancre gelée au centre
        assertEquals(MachineState.STATIONARY, result.nextState.machineState)
    }

    @Test
    fun movingState_advancesAnchorOnAcceptedSegment() {
        val current = sampleAt(1_000L)
        val anchor = sampleAt(0L)
        val result = engineWithDistance(12.5).apply(
            tripState = runningState(),
            filterState = FilterState(
                anchor = anchor,
                machineState = MachineState.MOVING,
                stationaryCenter = anchor
            ),
            currentSample = current
        )

        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, result.verdict)
        assertEquals(current, result.nextState.anchor)
    }

    @Test
    fun apply_fromEmptyState_setsAnchorAndIgnoresNoAnchor() {
        val current = sampleAt(0L)
        val result = engineWithDistance(12.5).apply(
            tripState = runningState(),
            filterState = FilterState(),
            currentSample = current
        )

        assertEquals(SampleVerdict.IGNORED_NO_ANCHOR, result.verdict)
        assertEquals(runningState(), result.state)
        assertEquals(current, result.nextState.anchor)
    }

    @Test
    fun movingState_transportsAnchorAndAccumulatesAcrossTicks() {
        val engine = engineWithDistance(12.5)

        // Tick 1 : en MOVING, segment accepté depuis l'ancre posée.
        val tick1 = engine.apply(
            tripState = runningState(),
            filterState = FilterState(
                anchor = sampleAt(0L),
                machineState = MachineState.MOVING,
                stationaryCenter = sampleAt(0L)
            ),
            currentSample = sampleAt(1_000L)
        )
        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, tick1.verdict)

        // Tick 2 : l'état de filtre transporté sert d'ancre -> nouveau segment.
        val tick2 = engine.apply(
            tripState = tick1.state,
            filterState = tick1.nextState,
            currentSample = sampleAt(2_000L)
        )

        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, tick2.verdict)
        assertEquals(125.0, tick2.state.totalDistanceMeters, 0.0) // 100 + 12.5 + 12.5
        assertEquals(45.0, tick2.state.partialDistanceMeters, 0.0) // 20 + 12.5 + 12.5
    }

    // --- Helpers ---

    private fun assertMirrorsEngine(
        engine: DistanceTripProgressEngine,
        anchor: LocationSample?,
        current: LocationSample,
        state: TripState,
        expectedVerdict: SampleVerdict
    ) {
        val reference = engine.applyLocationSampleWithVerdict(
            state = state,
            previousSample = anchor,
            currentSample = current
        )

        // En MOVING, apply() est le miroir exact de la primitive (P4.1 préservé).
        // Les cas sans ancre / session non active passent par la branche neutre,
        // indépendante de l'état machine.
        val result = engine.apply(
            tripState = state,
            filterState = FilterState(
                anchor = anchor,
                machineState = MachineState.MOVING,
                stationaryCenter = anchor
            ),
            currentSample = current
        )

        assertEquals(expectedVerdict, reference.verdict)
        assertEquals(reference.verdict, result.verdict)
        assertEquals(reference.state, result.state)
        // En MOVING, l'ancre suivante est toujours l'échantillon courant.
        assertEquals(current, result.nextState.anchor)
    }

    private fun runningState(): TripState {
        return TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Running
        )
    }

    private fun engineWithDistance(distanceMeters: Double): DistanceTripProgressEngine {
        return DistanceTripProgressEngine(
            distanceEngine = FakeDistanceEngine(distanceMeters = distanceMeters)
        )
    }

    private fun sampleAt(
        timestampMillis: Long,
        accuracyMeters: Double? = null,
        speedMetersPerSecond: Double? = null
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792,
                timestampMillis = timestampMillis
            ),
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond
        )
    }

    private class FakeDistanceEngine(
        private val distanceMeters: Double
    ) : DistanceEngine {
        override fun computeDistanceMeters(
            previous: GeoPoint,
            current: GeoPoint
        ): Double {
            return distanceMeters
        }
    }
}

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
 * ARSENAL RALLYE — GpsAccumulationFilter contract test (P3.a)
 *
 * Objet :
 * - Vérifie que le contrat `apply()` reproduit à l'identique la sémantique du
 *   moteur historique (`applyLocationSampleWithVerdict`) : mêmes verdicts, même
 *   état métier produit, pour chacune des branches du filtre.
 * - Documente explicitement la sémantique d'ancre héritée, conservée
 *   volontairement en P3.a/P3.b : l'ancre transportée avance vers l'échantillon
 *   courant à chaque application, y compris lorsque le verdict est un rejet.
 *
 * Aucune assertion ne dépend d'un comportement nouveau : ce test fige l'existant.
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

    // --- Sémantique d'ancre héritée (conservée volontairement jusqu'en P4) ---

    @Test
    fun apply_advancesAnchorEvenWhenSampleRejected() {
        val current = sampleAt(1_000L)

        // Une branche par verdict de rejet : dans tous les cas l'ancre suivante
        // devient l'échantillon courant — le rejet ne fige pas l'ancre.
        val rejectingCases = listOf(
            engineWithDistance(1.5) to sampleAt(0L), // REJECTED_NOISE
            engineWithDistance(100.0) to sampleAt(0L), // REJECTED_IMPLAUSIBLE_JUMP
        )

        rejectingCases.forEach { (engine, anchor) ->
            val result = engine.apply(
                tripState = runningState(),
                filterState = FilterState(anchor = anchor),
                currentSample = current
            )
            // L'état métier est inchangé (rejet)…
            assertEquals(runningState(), result.state)
            // …mais l'ancre a tout de même avancé.
            assertEquals(current, result.nextState.anchor)
        }

        // Cas vitesse quasi nulle (REJECTED_STATIONARY) : même règle.
        val stationaryCurrent = sampleAt(1_000L, speedMetersPerSecond = 0.3)
        val stationaryResult = engineWithDistance(12.0).apply(
            tripState = runningState(),
            filterState = FilterState(anchor = sampleAt(0L, speedMetersPerSecond = 0.3)),
            currentSample = stationaryCurrent
        )
        assertEquals(SampleVerdict.REJECTED_STATIONARY, stationaryResult.verdict)
        assertEquals(runningState(), stationaryResult.state)
        assertEquals(stationaryCurrent, stationaryResult.nextState.anchor)
    }

    @Test
    fun apply_advancesAnchorOnAcceptedSegment() {
        val current = sampleAt(1_000L)
        val result = engineWithDistance(12.5).apply(
            tripState = runningState(),
            filterState = FilterState(anchor = sampleAt(0L)),
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
    fun apply_transportedState_accumulatesAcrossTicks() {
        val engine = engineWithDistance(12.5)

        // Tick 1 : pas d'ancre -> IGNORED_NO_ANCHOR, l'ancre est posée.
        val first = engine.apply(
            tripState = runningState(),
            filterState = FilterState(),
            currentSample = sampleAt(0L)
        )

        // Tick 2 : l'état de filtre transporté sert d'ancre -> segment accepté.
        val second = engine.apply(
            tripState = first.state,
            filterState = first.nextState,
            currentSample = sampleAt(1_000L)
        )

        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, second.verdict)
        assertEquals(112.5, second.state.totalDistanceMeters, 0.0)
        assertEquals(32.5, second.state.partialDistanceMeters, 0.0)
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

        val result = engine.apply(
            tripState = state,
            filterState = FilterState(anchor = anchor),
            currentSample = current
        )

        assertEquals(expectedVerdict, reference.verdict)
        assertEquals(reference.verdict, result.verdict)
        assertEquals(reference.state, result.state)
        // L'ancre suivante est toujours l'échantillon courant (sémantique héritée).
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

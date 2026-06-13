package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.distance.DistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

/*
 * ARSENAL RALLYE — Filter tuning defaults (P4.a)
 *
 * Objet :
 * - Fige que le tuning par défaut reprend EXACTEMENT les constantes historiques
 *   du moteur (valeurs littérales ressaisies indépendamment).
 * - Fige que le moteur câblé sur le tuning par défaut est strictement équivalent
 *   au moteur câblé sur un tuning historique explicite, branche par branche.
 *
 * Preuve de neutralité de l'extraction P4.a : aucun seuil, aucun verdict, aucune
 * distance ne change. Les comportements aux seuils restent par ailleurs couverts
 * par DistanceTripProgressEngineTest (inchangé).
 */
class FilterTuningTest {

    @Test
    fun defaultTuning_reproducesHistoricalConstants() {
        val tuning = FilterTuning()

        assertEquals(2.0, tuning.noiseFloorMeters, 0.0)
        assertEquals(1.0, tuning.accuracyFloorFactor, 0.0)
        assertEquals(0.5, tuning.stationarySpeedMetersPerSecond, 0.0)
        assertEquals(200.0, tuning.maxPlausibleSpeedKmh, 0.0)
    }

    @Test
    fun defaultTuning_movingNoiseFloor_isLocked() {
        // P5.c-3 étape A : plancher réduit en MOVING. Verrou de la valeur par défaut.
        assertEquals(1.4, FilterTuning().movingNoiseFloorMeters, 0.0)
        // Le plancher historique reste 2.0 (STATIONARY / vitesse-absente inchangés).
        assertEquals(2.0, FilterTuning().noiseFloorMeters, 0.0)
    }

    @Test
    fun engineWithDefaultTuning_matchesExplicitHistoricalTuning() {
        // Tuning historique explicite, valeurs ressaisies indépendamment du défaut.
        val historical = FilterTuning(
            noiseFloorMeters = 2.0,
            accuracyFloorFactor = 1.0,
            stationarySpeedMetersPerSecond = 0.5,
            maxPlausibleSpeedKmh = 200.0
        )

        // Une branche par seuil, au voisinage de sa valeur historique.
        for (scenario in scenarios()) {
            val byDefault = engine(FilterTuning(), scenario.distanceMeters)
                .applyLocationSampleWithVerdict(
                    state = runningState(),
                    previousSample = scenario.previous,
                    currentSample = scenario.current
                )
            val byHistorical = engine(historical, scenario.distanceMeters)
                .applyLocationSampleWithVerdict(
                    state = runningState(),
                    previousSample = scenario.previous,
                    currentSample = scenario.current
                )

            assertEquals(scenario.name, byHistorical.verdict, byDefault.verdict)
            assertEquals(scenario.name, byHistorical.state, byDefault.state)
        }
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    private data class Scenario(
        val name: String,
        val previous: LocationSample,
        val current: LocationSample,
        val distanceMeters: Double
    )

    private fun scenarios(): List<Scenario> = listOf(
        // Vitesse quasi nulle (0.2 < 0.5) -> REJECTED_STATIONARY.
        Scenario("stationary", sampleAt(0L, speed = 0.2), sampleAt(1_000L, speed = 0.2), 12.0),
        // Segment sous le plancher de bruit (1.5 < 2.0) -> REJECTED_NOISE.
        Scenario("below_noise_floor", sampleAt(0L), sampleAt(1_000L), 1.5),
        // Sous le plancher d'incertitude (8 < max(2, 10*1.0)) -> REJECTED_NOISE.
        Scenario(
            "below_accuracy_floor",
            sampleAt(0L, accuracy = 10.0),
            sampleAt(1_000L, accuracy = 10.0),
            8.0
        ),
        // Saut implausible (100 m / 1 s = 360 km/h > 200) -> REJECTED_IMPLAUSIBLE_JUMP.
        Scenario("implausible_jump", sampleAt(0L), sampleAt(1_000L), 100.0),
        // Mouvement plausible (12.5 m) -> ACCEPTED_SEGMENT.
        Scenario("accepted", sampleAt(0L), sampleAt(1_000L), 12.5)
    )

    private fun engine(tuning: FilterTuning, distanceMeters: Double): DistanceTripProgressEngine {
        return DistanceTripProgressEngine(
            distanceEngine = FakeDistanceEngine(distanceMeters = distanceMeters),
            tuning = tuning
        )
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
        accuracy: Double? = null,
        speed: Double? = null
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792,
                timestampMillis = timestampMillis
            ),
            accuracyMeters = accuracy,
            speedMetersPerSecond = speed
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

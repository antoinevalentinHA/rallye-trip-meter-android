package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.distance.DistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Distance trip progress engine
 *
 * Rôle :
 * - Applique une mesure de localisation au TripState.
 * - Ajoute la distance parcourue au total et au partiel lorsque la session est active.
 *
 * Contraintes :
 * - Aucun accès GPS réel.
 * - Aucun lien avec Android Location.
 * - Aucun état mutable.
 * - Aucune dépendance UI.
 * - Aucune persistance.
 *
 * Principe :
 * - Ne fait rien sans échantillon précédent.
 * - Ne fait rien si la session n'est pas Running.
 * - Délègue le calcul métrique au DistanceEngine injecté.
 * - Ignore un échantillon dont la vitesse source indique une quasi-immobilité.
 * - Ignore un segment plus court que l'incertitude GPS (plancher lié a l'accuracy).
 * - Ignore les sauts implausibles (vitesse calculée trop élevée).
 * - Applique un coefficient de calibration global a la distance retenue.
 */
class DistanceTripProgressEngine(
    private val distanceEngine: DistanceEngine,
    private val calibrationFactor: Double = 1.0
) : TripProgressEngine {
    override fun applyLocationSample(
        state: TripState,
        previousSample: LocationSample?,
        currentSample: LocationSample
    ): TripState {
        if (previousSample == null) {
            return state
        }

        if (state.sessionState != TripSessionState.Running) {
            return state
        }

        if (isStationarySpeed(currentSample)) {
            return state
        }

        val distanceMeters = distanceEngine.computeDistanceMeters(
            previous = previousSample.point,
            current = currentSample.point
        )

        // Filtrage : immobilite (vitesse source), bruit/derive (plancher lie a
        // l'accuracy), et sauts implausibles. Hors perimetre : REFERENCE_ONLY / watchdog.
        if (distanceMeters < movementFloorMeters(previousSample, currentSample)) {
            return state
        }

        if (isImplausibleJump(previousSample, currentSample, distanceMeters)) {
            return state
        }

        val correctedDistanceMeters = distanceMeters * calibrationFactor

        return state.copy(
            totalDistanceMeters = state.totalDistanceMeters + correctedDistanceMeters,
            partialDistanceMeters = state.partialDistanceMeters + correctedDistanceMeters
        )
    }

    private fun isStationarySpeed(sample: LocationSample): Boolean {
        val speed = sample.speedMetersPerSecond ?: return false
        return speed < STATIONARY_SPEED_MPS
    }

    private fun movementFloorMeters(
        previousSample: LocationSample,
        currentSample: LocationSample
    ): Double {
        val accuracyFloor = worstAccuracyMeters(previousSample, currentSample) *
            ACCURACY_FLOOR_FACTOR
        return maxOf(NOISE_FLOOR_METERS, accuracyFloor)
    }

    private fun worstAccuracyMeters(
        previousSample: LocationSample,
        currentSample: LocationSample
    ): Double {
        val previousAccuracy = previousSample.accuracyMeters ?: 0.0
        val currentAccuracy = currentSample.accuracyMeters ?: 0.0
        return maxOf(previousAccuracy, currentAccuracy)
    }

    private fun isImplausibleJump(
        previousSample: LocationSample,
        currentSample: LocationSample,
        distanceMeters: Double
    ): Boolean {
        val previousMillis = previousSample.point.timestampMillis ?: return false
        val currentMillis = currentSample.point.timestampMillis ?: return false

        val elapsedMillis = currentMillis - previousMillis
        if (elapsedMillis <= 0L) {
            return true
        }

        val elapsedSeconds = elapsedMillis / MILLIS_PER_SECOND
        val speedKmh = distanceMeters / elapsedSeconds * METERS_PER_SECOND_TO_KMH
        return speedKmh > MAX_PLAUSIBLE_SPEED_KMH
    }

    private companion object {
        const val NOISE_FLOOR_METERS = 2.0
        const val ACCURACY_FLOOR_FACTOR = 1.0
        const val STATIONARY_SPEED_MPS = 0.5
        const val MAX_PLAUSIBLE_SPEED_KMH = 200.0
        const val MILLIS_PER_SECOND = 1000.0
        const val METERS_PER_SECOND_TO_KMH = 3.6
    }
}

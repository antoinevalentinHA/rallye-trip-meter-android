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
 * - Filtrage MVP : ignore le bruit GPS et les sauts implausibles.
 */
class DistanceTripProgressEngine(
    private val distanceEngine: DistanceEngine
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

        val distanceMeters = distanceEngine.computeDistanceMeters(
            previous = previousSample.point,
            current = currentSample.point
        )

        // Filtrage MVP : ignore le bruit GPS et les sauts implausibles.
        // Hors perimetre : modele REFERENCE_ONLY / watchdog / discontinuite.
        if (distanceMeters < NOISE_FLOOR_METERS) {
            return state
        }

        if (isImplausibleJump(previousSample, currentSample, distanceMeters)) {
            return state
        }

        return state.copy(
            totalDistanceMeters = state.totalDistanceMeters + distanceMeters,
            partialDistanceMeters = state.partialDistanceMeters + distanceMeters
        )
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
        const val MAX_PLAUSIBLE_SPEED_KMH = 200.0
        const val MILLIS_PER_SECOND = 1000.0
        const val METERS_PER_SECOND_TO_KMH = 3.6
    }
}

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

        return state.copy(
            totalDistanceMeters = state.totalDistanceMeters + distanceMeters,
            partialDistanceMeters = state.partialDistanceMeters + distanceMeters
        )
    }
}

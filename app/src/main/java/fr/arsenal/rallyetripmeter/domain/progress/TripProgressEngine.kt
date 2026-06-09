package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Trip progress engine contract
 *
 * Rôle :
 * - Définit le contrat d'application d'une mesure de localisation au trip meter.
 *
 * Contraintes :
 * - Contrat uniquement.
 * - Aucun accès GPS réel.
 * - Aucun lien avec Android Location.
 * - Aucun état mutable.
 * - Aucune dépendance UI.
 * - Aucune persistance.
 *
 * Principe :
 * - Reçoit l'état courant du trip.
 * - Reçoit l'échantillon précédent si disponible.
 * - Reçoit l'échantillon courant.
 * - Retourne un nouvel état TripState.
 */
interface TripProgressEngine {
    fun applyLocationSample(
        state: TripState,
        previousSample: LocationSample?,
        currentSample: LocationSample
    ): TripState
}

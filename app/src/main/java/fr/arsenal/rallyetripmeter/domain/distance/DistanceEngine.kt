package fr.arsenal.rallyetripmeter.domain.distance

import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint

/*
 * ARSENAL RALLYE — Distance engine contract
 *
 * Rôle :
 * - Définit le contrat de calcul de distance entre deux points géographiques métier.
 *
 * Contraintes :
 * - Contrat uniquement.
 * - Aucun accès GPS réel.
 * - Aucun lien avec Android Location.
 * - Aucune mutation d'état.
 * - Aucune persistance.
 *
 * Principe :
 * - Reçoit deux GeoPoint.
 * - Retourne une distance en mètres.
 */
interface DistanceEngine {
    fun computeDistanceMeters(
        previous: GeoPoint,
        current: GeoPoint
    ): Double
}

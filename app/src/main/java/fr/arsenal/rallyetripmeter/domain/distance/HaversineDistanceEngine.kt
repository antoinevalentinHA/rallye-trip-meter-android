package fr.arsenal.rallyetripmeter.domain.distance

import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * ARSENAL RALLYE — Haversine distance engine
 *
 * Rôle :
 * - Calcule la distance sphérique entre deux points géographiques métier.
 *
 * Contraintes :
 * - Aucun accès GPS réel.
 * - Aucun lien avec Android Location.
 * - Aucun état mutable.
 * - Aucune dépendance UI.
 * - Aucune persistance.
 *
 * Principe :
 * - Utilise la formule de Haversine.
 * - Ignore volontairement l'altitude.
 * - Retourne une distance en mètres.
 */
class HaversineDistanceEngine : DistanceEngine {
    override fun computeDistanceMeters(
        previous: GeoPoint,
        current: GeoPoint
    ): Double {
        val previousLatitudeRadians = Math.toRadians(previous.latitude)
        val currentLatitudeRadians = Math.toRadians(current.latitude)
        val latitudeDeltaRadians = Math.toRadians(current.latitude - previous.latitude)
        val longitudeDeltaRadians = Math.toRadians(current.longitude - previous.longitude)

        val haversineValue =
            sin(latitudeDeltaRadians / 2.0) * sin(latitudeDeltaRadians / 2.0) +
                cos(previousLatitudeRadians) *
                cos(currentLatitudeRadians) *
                sin(longitudeDeltaRadians / 2.0) *
                sin(longitudeDeltaRadians / 2.0)

        val angularDistance = 2.0 * atan2(
            sqrt(haversineValue),
            sqrt(1.0 - haversineValue)
        )

        return EARTH_RADIUS_METERS * angularDistance
    }

    private companion object {
        const val EARTH_RADIUS_METERS = 6_371_000.0
    }
}

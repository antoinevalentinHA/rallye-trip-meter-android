package fr.arsenal.rallyetripmeter.domain.geo

/*
 * ARSENAL RALLYE — Location sample model
 *
 * Rôle :
 * - Représente une mesure de localisation exploitable par le domaine.
 *
 * Contraintes :
 * - Aucun lien avec Android Location.
 * - Aucun accès GPS réel.
 * - Aucun calcul de distance.
 * - Modèle immutable.
 *
 * Principe :
 * - GeoPoint porte la position géographique.
 * - accuracyMeters porte la précision éventuelle de la mesure.
 * - speedMetersPerSecond porte la vitesse éventuelle fournie par la source.
 */
data class LocationSample(
    val point: GeoPoint,
    val accuracyMeters: Double? = null,
    val speedMetersPerSecond: Double? = null
)

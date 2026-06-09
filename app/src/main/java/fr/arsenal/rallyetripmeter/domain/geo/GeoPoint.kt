package fr.arsenal.rallyetripmeter.domain.geo

/*
 * ARSENAL RALLYE — Geo point model
 *
 * Rôle :
 * - Représente un point géographique métier minimal.
 *
 * Contraintes :
 * - Aucun lien avec Android Location.
 * - Aucun accès GPS réel.
 * - Aucun calcul de distance.
 * - Modèle immutable.
 *
 * Principe :
 * - Latitude et longitude exprimées en degrés décimaux.
 * - Altitude optionnelle, en mètres.
 * - Timestamp optionnel, en millisecondes epoch.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double? = null,
    val timestampMillis: Long? = null
)

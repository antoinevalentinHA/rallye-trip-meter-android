package fr.arsenal.rallyetripmeter.android.location

import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample

/*
 * ARSENAL RALLYE — Android location sample mapper
 *
 * Rôle :
 * - Convertit des valeurs primitives de localisation Android en LocationSample métier.
 *
 * Contraintes :
 * - Aucun import Android.
 * - Aucun accès GPS réel.
 * - Aucun état mutable.
 * - Aucune persistance.
 *
 * Principe :
 * - Seul point de conversion vers le domaine, testable en JVM pur.
 * - L'adaptateur Android lira android.location.Location et déléguera ici.
 */
internal fun buildLocationSample(
    latitude: Double,
    longitude: Double,
    altitudeMeters: Double?,
    timestampMillis: Long?,
    accuracyMeters: Double?,
    speedMetersPerSecond: Double?
): LocationSample {
    return LocationSample(
        point = GeoPoint(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = altitudeMeters,
            timestampMillis = timestampMillis
        ),
        accuracyMeters = accuracyMeters,
        speedMetersPerSecond = speedMetersPerSecond
    )
}

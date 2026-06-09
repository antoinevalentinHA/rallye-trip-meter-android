package fr.arsenal.rallyetripmeter.ui.viewmodel

import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample

/*
 * ARSENAL RALLYE — Simulated location samples
 *
 * Rôle :
 * - Fournit des échantillons de localisation simulés pour le palier local du ViewModel.
 *
 * Contraintes :
 * - Aucun GPS réel.
 * - Aucun Android Location.
 * - Aucun état mutable.
 * - Aucune persistance.
 *
 * Statut :
 * - Support temporaire de simulation avant branchement d'une source LocationEngine réelle.
 */
internal fun simulatedPreviousLocationSample(): LocationSample {
    return LocationSample(
        point = GeoPoint(
            latitude = 44.8378,
            longitude = -0.5792
        ),
        accuracyMeters = 4.0,
        speedMetersPerSecond = 12.0
    )
}

internal fun simulatedCurrentLocationSample(): LocationSample {
    return LocationSample(
        point = GeoPoint(
            latitude = 44.8380,
            longitude = -0.5794
        ),
        accuracyMeters = 4.0,
        speedMetersPerSecond = 12.0
    )
}

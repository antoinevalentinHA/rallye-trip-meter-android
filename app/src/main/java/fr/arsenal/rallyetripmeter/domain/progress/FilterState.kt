package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.geo.LocationSample

/*
 * ARSENAL RALLYE — Filter state (P3.a)
 *
 * Rôle :
 * - État opaque immutable transporté entre deux applications d'échantillon.
 *   Porte la propriété de l'ancre de l'accumulation GPS.
 *
 * Contraintes :
 * - Données pures uniquement, modèle immutable.
 * - Aucun lien avec l'UI, Android Location, ni la persistance.
 * - Aucune décision : ne filtre rien, ne calcule rien.
 * - Destiné à être transporté sans jamais être interprété par le runtime
 *   (I9 sémantique : l'état est porté, pas lu).
 *
 * Principe :
 * - v0 (P3.a) : porte uniquement le dernier échantillon retenu comme ancre,
 *   reproduisant à l'identique la sémantique actuelle de
 *   `TripRuntime.previousLocationSample`. Aucun autre champ.
 * - L'état vide (anchor == null) correspond à l'absence d'ancre initiale.
 */
data class FilterState(
    val anchor: LocationSample? = null,
)

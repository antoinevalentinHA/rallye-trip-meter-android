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
 *   `TripRuntime.previousLocationSample`.
 * - v1 (P4.1) : porte en plus l'état de la machine stationnaire/mouvement
 *   (`machineState`), son centre de référence (`stationaryCenter`) et les
 *   compteurs d'hystérésis (`movingStreak`, `stationaryStreak`). Ces champs sont
 *   OBSERVÉS et transportés, mais ne gouvernent pas encore l'accumulation :
 *   l'ancre avance toujours comme en P4.a. Le gate stationnaire arrive en P4.2.
 * - L'état vide (anchor == null) correspond à l'absence d'ancre initiale ;
 *   l'état machine par défaut est STATIONARY (un run démarre typiquement à
 *   l'arrêt — hypothèse, sans effet tant que le gate est inactif).
 */
data class FilterState(
    val anchor: LocationSample? = null,
    val machineState: MachineState = MachineState.STATIONARY,
    val stationaryCenter: LocationSample? = null,
    val movingStreak: Int = 0,
    val stationaryStreak: Int = 0,
)

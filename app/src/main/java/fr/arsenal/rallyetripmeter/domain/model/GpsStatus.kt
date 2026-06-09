package fr.arsenal.rallyetripmeter.domain.model

/*
 * ARSENAL RALLYE — Domain model
 *
 * Rôle :
 * - Représente l'état métier du signal GPS.
 *
 * Contraintes :
 * - Aucun lien avec l'UI.
 * - Aucun lien avec Android Location.
 * - Aucun calcul de distance.
 * - Aucun état mutable.
 */
enum class GpsStatus {
    Unavailable,
    Searching,
    Fixed,
}

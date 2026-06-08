package fr.arsenal.rallyetripmeter.domain.model

/*
 * ARSENAL RALLYE — Domain model
 *
 * Rôle :
 * - Représente l'état métier de la session trip.
 *
 * Contraintes :
 * - Aucun lien avec l'UI.
 * - Aucun contrôleur.
 * - Aucune mutation.
 */
enum class TripSessionState {
    Stopped,
    Running,
    Paused
}

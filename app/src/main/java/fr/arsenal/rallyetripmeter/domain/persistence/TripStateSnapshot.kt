package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Trip state snapshot
 *
 * Rôle :
 * - Capture minimale de l'état durable du trip meter.
 *
 * Contraintes :
 * - Données pures uniquement.
 * - N'embarque aucun champ GPS éphémère (statut, précision, vitesse).
 * - Aucun lien Android ni détail de stockage.
 */
data class TripStateSnapshot(
    val totalDistanceMeters: Double,
    val partialDistanceMeters: Double,
    val sessionState: TripSessionState
)

fun TripState.toTripStateSnapshot(): TripStateSnapshot {
    return TripStateSnapshot(
        totalDistanceMeters = totalDistanceMeters,
        partialDistanceMeters = partialDistanceMeters,
        sessionState = sessionState
    )
}

fun TripStateSnapshot.toTripState(): TripState {
    return TripState(
        sessionState = sessionState,
        totalDistanceMeters = totalDistanceMeters,
        partialDistanceMeters = partialDistanceMeters
    )
}

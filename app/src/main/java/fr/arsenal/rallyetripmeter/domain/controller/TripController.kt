package fr.arsenal.rallyetripmeter.domain.controller

import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Domain controller contract
 *
 * Rôle :
 * - Définit les actions métier autorisées sur l'état du trip meter.
 *
 * Contraintes :
 * - Contrat uniquement.
 * - Aucune implémentation.
 * - Aucun accès GPS.
 * - Aucun calcul de distance.
 * - Aucun état Android.
 * - Aucune dépendance UI.
 *
 * Principe :
 * - Chaque action reçoit un TripState.
 * - Chaque action retourne un nouveau TripState.
 * - Le contrat reste compatible avec un modèle immutable.
 */
interface TripController {
    fun start(state: TripState): TripState

    fun pause(state: TripState): TripState

    fun resume(state: TripState): TripState

    fun stop(state: TripState): TripState

    fun resetPartial(state: TripState): TripState

    fun resetTotal(state: TripState): TripState

    fun resetTrip(state: TripState): TripState

    fun adjustPartial(
        state: TripState,
        deltaMeters: Double
    ): TripState
}

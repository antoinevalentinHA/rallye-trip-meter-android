package fr.arsenal.rallyetripmeter.domain.controller

import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import kotlin.math.max

/*
 * ARSENAL RALLYE — Immutable trip controller
 *
 * Rôle :
 * - Implémente les transitions métier de base du trip meter.
 *
 * Contraintes :
 * - Modèle immutable.
 * - Aucune mutation interne.
 * - Aucun accès GPS.
 * - Aucun calcul de distance réelle.
 * - Aucun état Android.
 * - Aucune dépendance UI.
 *
 * Principe :
 * - Chaque action reçoit un TripState.
 * - Chaque action retourne un nouveau TripState.
 */
class ImmutableTripController : TripController {
    override fun start(state: TripState): TripState {
        return state.copy(
            sessionState = TripSessionState.Running
        )
    }

    override fun pause(state: TripState): TripState {
        if (state.sessionState != TripSessionState.Running) {
            return state
        }

        return state.copy(
            sessionState = TripSessionState.Paused
        )
    }

    override fun resume(state: TripState): TripState {
        if (state.sessionState != TripSessionState.Paused) {
            return state
        }

        return state.copy(
            sessionState = TripSessionState.Running
        )
    }

    override fun stop(state: TripState): TripState {
        return state.copy(
            sessionState = TripSessionState.Stopped
        )
    }

    override fun resetPartial(state: TripState): TripState {
        return state.copy(
            partialDistanceMeters = 0.0
        )
    }

    override fun resetTotal(state: TripState): TripState {
        return state.copy(
            totalDistanceMeters = 0.0
        )
    }

    override fun adjustPartial(
        state: TripState,
        deltaMeters: Double
    ): TripState {
        return state.copy(
            partialDistanceMeters = max(
                0.0,
                state.partialDistanceMeters + deltaMeters
            )
        )
    }
}

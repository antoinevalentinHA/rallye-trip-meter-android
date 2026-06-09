package fr.arsenal.rallyetripmeter.ui.model

/*
 * ARSENAL RALLYE — UI event model
 *
 * Rôle :
 * - Définit les événements utilisateur émis par l'écran TripMeter.
 *
 * Contraintes :
 * - Aucun accès GPS.
 * - Aucun contrôleur métier.
 * - Aucun état Android.
 * - Aucune logique de distance.
 *
 * Principe :
 * - Prépare une future entrée unique onEvent(event).
 * - Évite la prolifération de callbacks UI spécialisés.
 */
sealed interface TripMeterUiEvent {
    data object AdjustPartialMinus100 : TripMeterUiEvent

    data object AdjustPartialMinus10 : TripMeterUiEvent

    data object ResetPartial : TripMeterUiEvent

    data object AdjustPartialPlus10 : TripMeterUiEvent

    data object AdjustPartialPlus100 : TripMeterUiEvent

    data object SessionAction : TripMeterUiEvent

    data object Stop : TripMeterUiEvent

    data object Options : TripMeterUiEvent

    data object ApplyLocationSample : TripMeterUiEvent

    data object SimulateLocationStep : TripMeterUiEvent
}

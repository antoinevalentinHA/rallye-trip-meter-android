package fr.arsenal.rallyetripmeter.runtime

/*
 * ARSENAL RALLYE — Runtime event model
 *
 * Rôle :
 * - Langage d'entrée pur du TripRuntime, indépendant de l'UI.
 *
 * Contraintes :
 * - Aucune dépendance vers ui.* ni Android.
 * - 1:1 avec TripMeterUiEvent à ce stade ; la traduction UI -> runtime est
 *   faite par le ViewModel. Le runtime ne connaît plus le type UI.
 */
sealed interface TripRuntimeEvent {
    data object AdjustPartialMinus100 : TripRuntimeEvent

    data object AdjustPartialMinus10 : TripRuntimeEvent

    data object ResetPartial : TripRuntimeEvent

    data object AdjustPartialPlus10 : TripRuntimeEvent

    data object AdjustPartialPlus100 : TripRuntimeEvent

    data object SessionAction : TripRuntimeEvent

    data object Stop : TripRuntimeEvent

    data object ResetTotal : TripRuntimeEvent

    data object NewRun : TripRuntimeEvent

    data object ApplyLocationSample : TripRuntimeEvent

    data object SimulateLocationStep : TripRuntimeEvent
}

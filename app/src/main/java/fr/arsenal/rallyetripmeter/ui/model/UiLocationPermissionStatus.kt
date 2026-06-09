package fr.arsenal.rallyetripmeter.ui.model

/*
 * ARSENAL RALLYE — UI location permission status
 *
 * Rôle :
 * - Représente l'état de permission localisation affichable par l'UI.
 *
 * Contraintes :
 * - Ne dépend pas d'Android.
 * - Ne représente pas l'état GPS métier.
 * - Ne déclenche aucune demande de permission.
 *
 * Principe :
 * - Unknown : état non encore évalué.
 * - Granted : permission accordée.
 * - Denied : permission absente ou refusée.
 */
enum class UiLocationPermissionStatus(
    val label: String
) {
    Unknown(label = "POSITION ?"),
    Granted(label = "POSITION OK"),
    Denied(label = "POSITION REFUSÉE")
}

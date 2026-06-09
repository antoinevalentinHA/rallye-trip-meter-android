package fr.arsenal.rallyetripmeter.android.permission

/*
 * ARSENAL RALLYE — Location permission state
 *
 * Rôle :
 * - Représente l'état Android de permission de localisation.
 *
 * Contraintes :
 * - Ne représente pas l'état GPS métier.
 * - Ne dépend pas de l'UI.
 * - Ne déclenche aucune demande de permission.
 * - Ne lit aucun service Android.
 *
 * Principe :
 * - Unknown : état non encore évalué.
 * - Granted : permission accordée.
 * - Denied : permission refusée ou absente.
 */
enum class LocationPermissionState {
    Unknown,
    Granted,
    Denied
}

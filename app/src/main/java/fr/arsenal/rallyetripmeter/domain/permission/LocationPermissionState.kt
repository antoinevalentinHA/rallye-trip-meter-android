package fr.arsenal.rallyetripmeter.domain.permission

/*
 * ARSENAL RALLYE — Location permission state
 *
 * Rôle :
 * - Représente l'état de permission de localisation exploitable par le domaine et l'UI.
 *
 * Contraintes :
 * - Ne dépend pas d'Android.
 * - Ne représente pas l'état GPS métier.
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

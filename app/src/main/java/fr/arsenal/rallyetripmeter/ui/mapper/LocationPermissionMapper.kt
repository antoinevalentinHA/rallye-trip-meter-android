package fr.arsenal.rallyetripmeter.ui.mapper

import fr.arsenal.rallyetripmeter.android.permission.LocationPermissionState
import fr.arsenal.rallyetripmeter.ui.model.UiLocationPermissionStatus

/*
 * ARSENAL RALLYE — Location permission mapper
 *
 * Rôle :
 * - Convertit l'état Android de permission localisation vers un statut affichable UI.
 *
 * Contraintes :
 * - Ne déclenche aucune demande de permission.
 * - Ne lit aucun service Android.
 * - Ne représente pas l'état GPS métier.
 *
 * Principe :
 * - La couche UI ne dépend pas directement du libellé Android.
 * - Le statut affichable reste distinct du GpsStatus métier.
 */
fun LocationPermissionState.toUiLocationPermissionStatus(): UiLocationPermissionStatus {
    return when (this) {
        LocationPermissionState.Unknown -> UiLocationPermissionStatus.Unknown
        LocationPermissionState.Granted -> UiLocationPermissionStatus.Granted
        LocationPermissionState.Denied -> UiLocationPermissionStatus.Denied
    }
}

package fr.arsenal.rallyetripmeter.android.permission

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/*
 * ARSENAL RALLYE — Location permission checker
 *
 * Rôle :
 * - Lit l'état Android réel des permissions de localisation.
 *
 * Contraintes :
 * - Ne déclenche aucune demande de permission.
 * - N'affiche aucune popup système.
 * - Ne lit aucun GPS.
 * - Ne représente pas l'état GPS métier.
 *
 * Principe :
 * - Granted si la permission fine ou approximative est accordée.
 * - Denied sinon.
 */
class LocationPermissionChecker(
    private val context: Context
) {
    fun getLocationPermissionState(): LocationPermissionState {
        val fineLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocationGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (fineLocationGranted || coarseLocationGranted) {
            return LocationPermissionState.Granted
        }

        return LocationPermissionState.Denied
    }
}

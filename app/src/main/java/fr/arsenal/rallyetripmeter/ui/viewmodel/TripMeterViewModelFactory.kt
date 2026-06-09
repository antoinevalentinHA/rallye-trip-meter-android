package fr.arsenal.rallyetripmeter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.arsenal.rallyetripmeter.android.location.AndroidLocationEngine
import fr.arsenal.rallyetripmeter.android.permission.LocationPermissionChecker

/*
 * ARSENAL RALLYE — Trip meter ViewModel factory
 *
 * Rôle :
 * - Crée le TripMeterViewModel avec ses dépendances Android réelles.
 * - Injecte AndroidLocationEngine à partir d'un Context applicatif.
 * - Injecte l'état initial réel des permissions de localisation.
 *
 * Contraintes :
 * - Aucun démarrage GPS.
 * - Aucune demande de permission runtime.
 * - Aucun accès direct à android.location.Location.
 * - Aucun effet de bord de localisation.
 *
 * Statut :
 * - Couture d'injection avant implémentation réelle de LocationManager.
 */
class TripMeterViewModelFactory(
    private val context: Context
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {
        if (modelClass.isAssignableFrom(TripMeterViewModel::class.java)) {
            val applicationContext = context.applicationContext

            val locationPermissionChecker = LocationPermissionChecker(
                context = applicationContext
            )

            return TripMeterViewModel(
                initialLocationPermissionState = locationPermissionChecker.getLocationPermissionState(),
                readLocationPermissionState = locationPermissionChecker::getLocationPermissionState,
                locationEngine = AndroidLocationEngine(
                    context = applicationContext
                )
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

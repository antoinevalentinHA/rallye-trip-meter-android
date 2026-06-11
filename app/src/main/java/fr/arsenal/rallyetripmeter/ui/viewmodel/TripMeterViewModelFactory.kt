package fr.arsenal.rallyetripmeter.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import fr.arsenal.rallyetripmeter.android.diag.TickLogSinkHolder
import fr.arsenal.rallyetripmeter.android.location.LocationEngineHolder
import fr.arsenal.rallyetripmeter.android.permission.LocationPermissionChecker
import fr.arsenal.rallyetripmeter.android.persistence.SharedPreferencesCalibrationStore
import fr.arsenal.rallyetripmeter.android.persistence.SharedPreferencesTripStateStore
import fr.arsenal.rallyetripmeter.android.service.TripMeterForegroundServiceController
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.persistence.toTripState
import fr.arsenal.rallyetripmeter.runtime.TripRuntimeHolder

/*
 * ARSENAL RALLYE — Trip meter ViewModel factory
 *
 * Rôle :
 * - Crée le TripMeterViewModel avec ses dépendances Android réelles.
 * - Injecte AndroidLocationEngine à partir d'un Context applicatif.
 * - Injecte l'état initial réel des permissions de localisation.
 * - Crée le TripStateStore SharedPreferences et restaure l'état persisté.
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

            val locationEngine = LocationEngineHolder.getEngine(
                context = applicationContext
            )

            val tripStateStore = SharedPreferencesTripStateStore(
                context = applicationContext
            )

            val calibrationStore = SharedPreferencesCalibrationStore(
                context = applicationContext
            )

            val initialTripState = tripStateStore.load()?.toTripState()
                ?: TripState(gpsStatus = locationEngine.getGpsStatus())

            val runtime = TripRuntimeHolder.getRuntime(
                locationEngine = locationEngine,
                tripStateStore = tripStateStore,
                initialState = initialTripState,
                tickLogSink = TickLogSinkHolder.getSink()
            )

            val foregroundServiceController = TripMeterForegroundServiceController(
                context = applicationContext
            )

            return TripMeterViewModel(
                initialLocationPermissionState = locationPermissionChecker.getLocationPermissionState(),
                readLocationPermissionState = locationPermissionChecker::getLocationPermissionState,
                locationEngine = locationEngine,
                startForegroundService = foregroundServiceController::start,
                stopForegroundService = foregroundServiceController::stop,
                tripStateStore = tripStateStore,
                calibrationStore = calibrationStore,
                initialTripState = initialTripState,
                runtime = runtime
            ) as T
        }

        throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
    }
}

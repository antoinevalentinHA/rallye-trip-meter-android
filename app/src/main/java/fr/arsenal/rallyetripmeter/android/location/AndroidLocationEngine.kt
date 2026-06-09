package fr.arsenal.rallyetripmeter.android.location

import android.content.Context
import android.location.LocationManager
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus

/*
 * ARSENAL RALLYE — Android location engine skeleton
 *
 * Rôle :
 * - Pose la frontière Android vers le contrat métier LocationEngine.
 * - Prépare la surface lifecycle start/stop de la future localisation réelle.
 * - Lit l'état de disponibilité des providers Android de localisation.
 *
 * Contraintes :
 * - Aucun abonnement GPS réel.
 * - Aucun requestLocationUpdates.
 * - Aucune demande de permission runtime.
 * - Aucun Android Location exposé au domaine.
 * - Aucune persistance.
 * - Aucun effet de bord GPS actif.
 *
 * Statut :
 * - Squelette LocationManager sans acquisition de position.
 */
class AndroidLocationEngine(
    context: Context
) : LocationEngine {
    private val applicationContext: Context = context.applicationContext

    private val locationManager: LocationManager? =
        applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun start() {
        // No-op volontaire : l'abonnement GPS réel sera introduit dans un commit dédié.
    }

    fun stop() {
        // No-op volontaire : l'abonnement GPS réel sera introduit dans un commit dédié.
    }

    override fun getGpsStatus(): GpsStatus {
        if (isLocationProviderEnabled()) {
            return GpsStatus.Searching
        }

        return GpsStatus.Unavailable
    }

    override fun getLastLocationSample(): LocationSample? {
        return null
    }

    private fun isLocationProviderEnabled(): Boolean {
        return isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun isProviderEnabled(
        provider: String
    ): Boolean {
        return try {
            locationManager?.isProviderEnabled(provider) == true
        } catch (_: IllegalArgumentException) {
            false
        }
    }
}

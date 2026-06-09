package fr.arsenal.rallyetripmeter.android.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus

/*
 * ARSENAL RALLYE — Android location engine
 *
 * Rôle :
 * - Pose la frontière Android vers le contrat métier LocationEngine.
 * - Abonne LocationManager au GPS et met en cache le dernier échantillon.
 * - Expose le statut GPS métier et le dernier échantillon disponible.
 *
 * Contraintes :
 * - Aucune demande de permission runtime (garde assurée côté ViewModel).
 * - Aucun Android Location exposé au domaine (conversion via le mapper).
 * - Aucune persistance.
 * - Aucun FusedLocationProviderClient.
 * - Aucun Flow ni coroutine.
 *
 * Statut :
 * - Abonnement GPS réel actif, cache lisible par getLastLocationSample().
 * - Aucun pump : la consommation du cache reste de la responsabilité de l'appelant.
 */
class AndroidLocationEngine(
    context: Context
) : LocationEngine {
    private val applicationContext: Context = context.applicationContext

    private val locationManager: LocationManager? =
        applicationContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    @Volatile
    private var lastLocationSample: LocationSample? = null

    private var isListening: Boolean = false

    private val locationListener: LocationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            lastLocationSample = location.toLocationSample()
        }

        override fun onProviderEnabled(provider: String) {
            // No-op : la disponibilité est relue dynamiquement par getGpsStatus().
        }

        override fun onProviderDisabled(provider: String) {
            // No-op : getGpsStatus() retombe sur Unavailable via isLocationProviderEnabled().
        }

        @Deprecated("Conservé pour la compatibilité API < 30.")
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?
        ) {
            // No-op volontaire.
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isListening) {
            return
        }

        val manager = locationManager ?: return

        try {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                MIN_TIME_MS,
                MIN_DISTANCE_METERS,
                locationListener
            )
            isListening = true
        } catch (_: SecurityException) {
            isListening = false
        } catch (_: IllegalArgumentException) {
            isListening = false
        }
    }

    fun stop() {
        if (!isListening) {
            return
        }

        try {
            locationManager?.removeUpdates(locationListener)
        } catch (_: SecurityException) {
            // Défensif : révocation concurrente possible.
        } finally {
            isListening = false
        }
    }

    override fun getGpsStatus(): GpsStatus {
        if (!isLocationProviderEnabled()) {
            return GpsStatus.Unavailable
        }

        return if (lastLocationSample != null) {
            GpsStatus.Fixed
        } else {
            GpsStatus.Searching
        }
    }

    override fun getLastLocationSample(): LocationSample? {
        return lastLocationSample
    }

    private fun Location.toLocationSample(): LocationSample {
        return buildLocationSample(
            latitude = latitude,
            longitude = longitude,
            altitudeMeters = if (hasAltitude()) altitude else null,
            timestampMillis = time,
            accuracyMeters = if (hasAccuracy()) accuracy.toDouble() else null,
            speedMetersPerSecond = if (hasSpeed()) speed.toDouble() else null
        )
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

    private companion object {
        const val MIN_TIME_MS: Long = 1000L
        const val MIN_DISTANCE_METERS: Float = 0f
    }
}

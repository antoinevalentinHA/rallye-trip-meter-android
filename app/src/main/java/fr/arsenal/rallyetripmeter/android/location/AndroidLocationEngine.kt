package fr.arsenal.rallyetripmeter.android.location

import android.content.Context
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus

/*
 * ARSENAL RALLYE — Android location engine skeleton
 *
 * Rôle :
 * - Pose la frontière Android vers le contrat métier LocationEngine.
 * - Prépare la surface lifecycle start/stop de la future localisation réelle.
 *
 * Contraintes :
 * - Aucun accès GPS réel.
 * - Aucune demande de permission runtime.
 * - Aucun Android Location exposé au domaine.
 * - Aucune persistance.
 * - Aucun effet de bord GPS.
 *
 * Statut :
 * - Squelette temporaire avant implémentation réelle de LocationManager.
 */
class AndroidLocationEngine(
    context: Context
) : LocationEngine {
    private val applicationContext: Context = context.applicationContext

    fun start() {
        // No-op volontaire : la localisation réelle sera introduite dans un commit dédié.
    }

    fun stop() {
        // No-op volontaire : la localisation réelle sera introduite dans un commit dédié.
    }

    override fun getGpsStatus(): GpsStatus {
        return GpsStatus.Unavailable
    }

    override fun getLastLocationSample(): LocationSample? {
        return null
    }
}


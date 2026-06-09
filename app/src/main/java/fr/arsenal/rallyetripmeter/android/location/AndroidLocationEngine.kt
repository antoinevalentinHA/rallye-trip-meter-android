package fr.arsenal.rallyetripmeter.android.location

import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus

/*
 * ARSENAL RALLYE — Android location engine skeleton
 *
 * Rôle :
 * - Pose la frontière Android vers le contrat métier LocationEngine.
 *
 * Contraintes :
 * - Aucun accès GPS réel.
 * - Aucune demande de permission runtime.
 * - Aucun Android Location exposé au domaine.
 * - Aucune persistance.
 * - Aucun effet de bord.
 *
 * Statut :
 * - Squelette temporaire avant implémentation réelle de la localisation Android.
 */
class AndroidLocationEngine : LocationEngine {
    override fun getGpsStatus(): GpsStatus {
        return GpsStatus.Unavailable
    }

    override fun getLastLocationSample(): LocationSample? {
        return null
    }
}

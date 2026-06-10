package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.model.TripSessionState

/*
 * ARSENAL RALLYE — Periodic save throttle
 *
 * Rôle :
 * - Décide si une sauvegarde périodique de l'état durable est due.
 * - Cadence pilotée par une horloge injectée (testable hors Android).
 *
 * Contraintes :
 * - Données pures, aucune dépendance Android.
 * - Ne propose une sauvegarde que si la session est Running.
 * - Respecte un intervalle minimal entre deux sauvegardes.
 * - N'autorise pas de sauvegarde si l'état durable n'a pas changé.
 */
class PeriodicSaveThrottle(
    private val intervalMillis: Long = DEFAULT_INTERVAL_MILLIS,
    private val nowMillis: () -> Long
) {
    private var lastSaveAtMillis: Long? = null
    private var lastSavedSnapshot: TripStateSnapshot? = null

    fun pollSnapshotToSave(snapshot: TripStateSnapshot): TripStateSnapshot? {
        if (snapshot.sessionState != TripSessionState.Running) {
            return null
        }

        val now = nowMillis()
        val last = lastSaveAtMillis

        if (last != null && now - last < intervalMillis) {
            return null
        }

        if (snapshot == lastSavedSnapshot) {
            return null
        }

        lastSaveAtMillis = now
        lastSavedSnapshot = snapshot

        return snapshot
    }

    companion object {
        const val DEFAULT_INTERVAL_MILLIS = 15_000L
    }
}

package fr.arsenal.rallyetripmeter.android.persistence

import android.content.Context
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateSnapshot
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateStore

/*
 * ARSENAL RALLYE — SharedPreferences trip state store
 *
 * Rôle :
 * - Implémentation Android du port TripStateStore via SharedPreferences.
 * - Persiste uniquement les champs durables (total, partiel, état de session).
 *
 * Contraintes :
 * - Aucun champ GPS éphémère persisté.
 * - Aucune dépendance externe (DataStore / Room).
 * - load() renvoie null en l'absence de snapshot ou si l'état de session est invalide.
 */
class SharedPreferencesTripStateStore(
    context: Context
) : TripStateStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun save(snapshot: TripStateSnapshot) {
        preferences.edit()
            .putLong(KEY_TOTAL_BITS, snapshot.totalDistanceMeters.toRawBits())
            .putLong(KEY_PARTIAL_BITS, snapshot.partialDistanceMeters.toRawBits())
            .putString(KEY_SESSION_STATE, snapshot.sessionState.name)
            .apply()
    }

    override fun load(): TripStateSnapshot? {
        val sessionName = preferences.getString(KEY_SESSION_STATE, null)
            ?: return null

        val sessionState = TripSessionState.entries.firstOrNull { it.name == sessionName }
            ?: return null

        return TripStateSnapshot(
            totalDistanceMeters = Double.fromBits(preferences.getLong(KEY_TOTAL_BITS, 0L)),
            partialDistanceMeters = Double.fromBits(preferences.getLong(KEY_PARTIAL_BITS, 0L)),
            sessionState = sessionState
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "rallye_trip_meter_trip_state"
        const val KEY_TOTAL_BITS = "total_distance_meters_bits"
        const val KEY_PARTIAL_BITS = "partial_distance_meters_bits"
        const val KEY_SESSION_STATE = "session_state"
    }
}

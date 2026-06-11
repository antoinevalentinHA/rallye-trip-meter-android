package fr.arsenal.rallyetripmeter.android.persistence

import android.content.Context
import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient
import fr.arsenal.rallyetripmeter.domain.persistence.CalibrationStore

/*
 * ARSENAL RALLYE — SharedPreferences calibration store
 *
 * Rôle :
 * - Implémentation Android du port CalibrationStore via SharedPreferences.
 * - Stockage dédié, séparé de la persistance du trip state.
 *
 * Contraintes :
 * - Valeur stockée en millièmes (Int). Absente -> coefficient par défaut (1.000).
 * - Écrivain unique. Aucune dépendance externe (DataStore / Room).
 */
class SharedPreferencesCalibrationStore(
    context: Context
) : CalibrationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    override fun save(coefficient: CalibrationCoefficient) {
        preferences.edit()
            .putInt(KEY_PER_MILLE, coefficient.perMille)
            .apply()
    }

    override fun load(): CalibrationCoefficient {
        val perMille = preferences.getInt(KEY_PER_MILLE, CalibrationCoefficient.DEFAULT_PER_MILLE)
        return CalibrationCoefficient.of(perMille)
    }

    private companion object {
        const val PREFERENCES_NAME = "rallye_trip_meter_calibration"
        const val KEY_PER_MILLE = "calibration_per_mille"
    }
}

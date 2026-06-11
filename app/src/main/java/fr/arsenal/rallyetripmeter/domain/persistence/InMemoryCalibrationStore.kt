package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient

/*
 * ARSENAL RALLYE — In-memory calibration store
 *
 * Rôle :
 * - Implémentation pure (sans Android) du port CalibrationStore.
 * - Défaut neutre 1.000 tant qu'aucune valeur n'est écrite. Sert aussi aux tests.
 *
 * Contraintes :
 * - Aucun effet de bord externe.
 */
class InMemoryCalibrationStore(
    initial: CalibrationCoefficient = CalibrationCoefficient.default()
) : CalibrationStore {
    private var current: CalibrationCoefficient = initial

    override fun save(coefficient: CalibrationCoefficient) {
        current = coefficient
    }

    override fun load(): CalibrationCoefficient = current
}

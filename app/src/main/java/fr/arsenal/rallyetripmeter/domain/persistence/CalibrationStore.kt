package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient

/*
 * ARSENAL RALLYE — Calibration store port
 *
 * Rôle :
 * - Port pur de persistance du coefficient de calibration manuelle.
 *
 * Contraintes :
 * - Séparé de la persistance du trip state.
 * - load() renvoie le coefficient par défaut (1.000) en l'absence de valeur stockée.
 * - Aucun détail de stockage (Android, SharedPreferences, fichier) dans le domaine.
 */
interface CalibrationStore {
    fun save(coefficient: CalibrationCoefficient)

    fun load(): CalibrationCoefficient
}

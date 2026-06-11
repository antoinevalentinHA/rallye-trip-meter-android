package fr.arsenal.rallyetripmeter.domain.calibration

/*
 * ARSENAL RALLYE — Coefficient de calibration manuelle
 *
 * Rôle :
 * - Valeur immuable du coefficient de calibration manuelle, exprimée en millièmes (pour-mille).
 *   1000 = 1.000 (neutre). À terme : distance calibrée = distance brute × toDouble().
 *
 * Contraintes :
 * - Représentation entière → aucune dérive flottante sur les pas.
 * - Bornée à [900, 1100] (soit 0.900 à 1.100).
 * - Pur : aucune connaissance du runtime / moteur GPS / service foreground / filtre / UI.
 * - Ce palier (socle) n'applique le coefficient à AUCUNE distance affichée.
 */
@JvmInline
value class CalibrationCoefficient private constructor(val perMille: Int) {

    /** Coefficient en Double (ex. 1020 -> 1.02). */
    fun toDouble(): Double = perMille / 1000.0

    /** Format stable et indépendant de la locale (ex. "1.000", "1.020", "0.995"). */
    fun format(): String {
        val whole = perMille / 1000
        val frac = (perMille % 1000).toString().padStart(3, '0')
        return "$whole.$frac"
    }

    /** Nouveau coefficient ajusté du pas donné, borné. */
    fun adjustedBy(stepPerMille: Int): CalibrationCoefficient = of(perMille + stepPerMille)

    /** Retour au coefficient neutre (1.000). */
    fun reset(): CalibrationCoefficient = default()

    /** Vrai si le coefficient est différent du neutre (1.000). */
    fun isActive(): Boolean = perMille != DEFAULT_PER_MILLE

    companion object {
        const val DEFAULT_PER_MILLE = 1000
        const val MIN_PER_MILLE = 900
        const val MAX_PER_MILLE = 1100
        const val STEP_SMALL = 1    // ±0.001
        const val STEP_LARGE = 10   // ±0.010

        /** Construit un coefficient borné à [MIN_PER_MILLE, MAX_PER_MILLE]. */
        fun of(perMille: Int): CalibrationCoefficient =
            CalibrationCoefficient(perMille.coerceIn(MIN_PER_MILLE, MAX_PER_MILLE))

        /** Coefficient neutre (1.000). */
        fun default(): CalibrationCoefficient = of(DEFAULT_PER_MILLE)
    }
}

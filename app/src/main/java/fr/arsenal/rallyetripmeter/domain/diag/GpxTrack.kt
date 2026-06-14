package fr.arsenal.rallyetripmeter.domain.diag

/*
 * ARSENAL RALLYE — Modèle de trace GPX (relecture a posteriori)
 *
 * Rôle :
 * - Représente une trace lue depuis un fichier GPX déjà exporté, pour relecture
 *   hors session (souvenir / historique). Modèle pur, immutable.
 *
 * Contraintes :
 * - Données pures uniquement : aucune dépendance Android, aucun calcul de distance,
 *   aucun lien au moteur. Ce modèle ne participe JAMAIS au calcul du trip meter.
 */
data class GpxTrackPoint(
    val latitude: Double,
    val longitude: Double,
    /** Epoch (ms) du point, si le GPX portait un <time>. */
    val timeMillis: Long?,
)

data class GpxTrack(
    /** Nom du <trk><name>, si présent. */
    val name: String?,
    val points: List<GpxTrackPoint>,
) {
    val pointCount: Int get() = points.size

    /** Epoch (ms) du premier point horodaté, si disponible. */
    val startTimeMillis: Long? get() = points.firstNotNullOfOrNull { it.timeMillis }

    /** Epoch (ms) du dernier point horodaté, si disponible. */
    val endTimeMillis: Long?
        get() = points.lastOrNull { it.timeMillis != null }?.timeMillis

    /** Durée (ms) entre premier et dernier point horodaté, si calculable et ≥ 0. */
    val durationMillis: Long?
        get() {
            val start = startTimeMillis ?: return null
            val end = endTimeMillis ?: return null
            val delta = end - start
            return if (delta >= 0) delta else null
        }
}

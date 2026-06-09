package fr.arsenal.rallyetripmeter.domain.model

/*
 * ARSENAL RALLYE — Domain model
 *
 * Rôle :
 * - Représente un instantané métier complet du trip meter.
 *
 * Contraintes :
 * - Modèle immutable.
 * - Aucun lien avec l'UI.
 * - Aucun lien avec Android Location.
 * - Aucun calcul de distance.
 * - Aucun comportement de contrôle.
 */
data class TripState(
    val sessionState: TripSessionState = TripSessionState.Stopped,
    val gpsStatus: GpsStatus = GpsStatus.Unavailable,
    val totalDistanceMeters: Double = 0.0,
    val partialDistanceMeters: Double = 0.0,
    val accuracyMeters: Double? = null,
)

package fr.arsenal.rallyetripmeter.domain.progress

/*
 * ARSENAL RALLYE — Filter tuning (P4.a)
 *
 * Rôle :
 * - Externalise les constantes de filtrage GPS du moteur d'accumulation en un
 *   objet de valeurs nommées, injectable.
 *
 * Contraintes :
 * - Données pures uniquement, modèle immutable.
 * - Aucun lien avec l'UI, Android Location, ni la persistance.
 * - Aucune décision, aucun calcul : ne porte que des seuils nommés.
 *
 * Principe :
 * - Les valeurs par défaut reproduisent à l'identique les constantes historiques
 *   portées jusque-là par le companion de DistanceTripProgressEngine
 *   (NOISE_FLOOR_METERS, ACCURACY_FLOOR_FACTOR, STATIONARY_SPEED_MPS,
 *   MAX_PLAUSIBLE_SPEED_KMH). Comportement strictement constant en P4.a.
 * - P5 pourra injecter une instance accordée ; P4.b enrichira la logique sans
 *   changer ce contrat de valeurs.
 * - Les conversions d'unités (ms -> s, m/s -> km/h) ne sont pas du tuning :
 *   elles restent internes au moteur.
 */
data class FilterTuning(
    /** Plancher de bruit minimal, en mètres (NOISE_FLOOR_METERS historique). */
    val noiseFloorMeters: Double = 2.0,
    /** Facteur appliqué au plancher d'incertitude (ACCURACY_FLOOR_FACTOR historique). */
    val accuracyFloorFactor: Double = 1.0,
    /** Seuil de quasi-immobilité, en m/s (STATIONARY_SPEED_MPS historique). */
    val stationarySpeedMetersPerSecond: Double = 0.5,
    /** Vitesse implicite maximale plausible, en km/h (MAX_PLAUSIBLE_SPEED_KMH historique). */
    val maxPlausibleSpeedKmh: Double = 200.0,
)

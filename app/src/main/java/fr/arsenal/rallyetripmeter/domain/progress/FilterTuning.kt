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
    /*
     * P4.1 — constantes de détection stationnaire/mouvement. SANS effet sur
     * l'accumulation tant que le gate stationnaire n'est pas activé (P4.2) :
     * elles ne servent qu'au calcul de l'état machine observé. Valeurs par
     * défaut = hypothèses, à régler par replay en P4.2.
     */
    /** Déplacement net (m) au-delà duquel on quitte la zone d'arrêt. */
    val movementTriggerMeters: Double = 15.0,
    /** Déplacement pas-à-pas (m) en deçà duquel on considère l'appareil immobile. */
    val stillnessRadiusMeters: Double = 3.0,
    /** Nombre d'échantillons consécutifs confirmant une transition (anti-flapping). */
    val detectionHysteresisSamples: Int = 3,
)

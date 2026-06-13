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
    /**
     * Plancher de bruit appliqué UNIQUEMENT en mouvement confirmé (état MOVING),
     * en mètres. Plus bas que noiseFloorMeters : à ~1 Hz, un pas piéton lent
     * (~1,2 m/tick) passait sous 2 m et était rejeté en REJECTED_NOISE
     * (marche lente sous-comptée ≈ −89 %, cf. P5.c-1). Confiné à MOVING : le gate
     * stationnaire et la branche vitesse-absente gardent noiseFloorMeters (P5.c-3 étape A).
     */
    val movingNoiseFloorMeters: Double = 1.4,
    /** Facteur appliqué au plancher d'incertitude (ACCURACY_FLOOR_FACTOR historique). */
    val accuracyFloorFactor: Double = 1.0,
    /** Seuil de quasi-immobilité, en m/s (STATIONARY_SPEED_MPS historique). */
    val stationarySpeedMetersPerSecond: Double = 0.5,
    /** Vitesse implicite maximale plausible, en km/h (MAX_PLAUSIBLE_SPEED_KMH historique). */
    val maxPlausibleSpeedKmh: Double = 200.0,
    /*
     * P4.2 — constantes de détection stationnaire/mouvement, ACTIVES (gouvernent
     * le gate). Réglées par replay du corpus M1/M2 :
     * - stillnessRadiusMeters=1.0 : à 3.0 (≈10.8 km/h) la conduite urbaine lente
     *   était classée à tort « immobile » → bascule STATIONARY parasite et
     *   distorsion (+64 % sur un log urbain). 1.0 (≈3.6 km/h) ne traite en arrêt
     *   que la quasi-immobilité réelle.
     * - detectionHysteresisSamples=8 : 8 échantillons consécutifs (~8 s à 1 Hz)
     *   pour confirmer une transition → anti-flapping, préserve le mouvement.
     * Ce ne sont pas des coefficients de correction (aucun facteur sur la distance).
     */
    /** Déplacement net (m) au-delà duquel on quitte la zone d'arrêt. */
    val movementTriggerMeters: Double = 15.0,
    /** Déplacement pas-à-pas (m) en deçà duquel on considère l'appareil immobile. */
    val stillnessRadiusMeters: Double = 1.0,
    /** Nombre d'échantillons consécutifs confirmant une transition (anti-flapping). */
    val detectionHysteresisSamples: Int = 8,
)

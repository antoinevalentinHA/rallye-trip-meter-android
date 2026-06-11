package fr.arsenal.rallyetripmeter.domain.diag

/*
 * ARSENAL RALLYE — Sample verdict model
 *
 * Rôle :
 * - Énumération fermée des verdicts d'observabilité d'un tick GPS (invariant I11 :
 *   chaque tick reçoit exactement un verdict explicable).
 *
 * Contraintes :
 * - Aucun lien avec l'UI.
 * - Aucun lien avec Android Location.
 * - Aucun calcul de distance.
 * - Aucune décision : ce type décrit, il ne filtre pas.
 *
 * Principe :
 * - P1 : les verdicts reflètent en miroir les branches existantes du pipeline,
 *   sans introduire de nouvelle logique de décision.
 * - P4 (machine STATIONNAIRE/MOUVEMENT) étendra cette énumération
 *   (ACCEPTED_NET_ON_TRANSITION, REJECTED_STALE, REANCHORED_AFTER_GAP).
 */
enum class SampleVerdict {
    /** Segment accepté et accumulé (branche nominale du moteur). */
    ACCEPTED_SEGMENT,

    /** Rejet : vitesse source sous le seuil de quasi-immobilité. */
    REJECTED_STATIONARY,

    /** Rejet : segment sous le plancher bruit/incertitude (noise floor / accuracy floor). */
    REJECTED_NOISE,

    /** Rejet : saut implausible (vitesse implicite trop élevée ou délai non positif). */
    REJECTED_IMPLAUSIBLE_JUMP,

    /** Ignoré : session non Running (Stopped ou Paused). */
    IGNORED_NOT_RUNNING,

    /** Ignoré : aucun échantillon précédent exploitable comme référence. */
    IGNORED_NO_ANCHOR,

    /** Ignoré : échantillon identique au précédent (relecture du cache sans nouveau fix). */
    IGNORED_DUPLICATE,

    /** Ignoré : aucun échantillon disponible au tick (cache de localisation vide). */
    IGNORED_NO_SAMPLE,
}

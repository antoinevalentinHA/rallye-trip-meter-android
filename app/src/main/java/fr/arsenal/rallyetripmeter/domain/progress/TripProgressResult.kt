package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Trip progress result model
 *
 * Rôle :
 * - Porte le résultat observable d'une application d'échantillon : le nouvel
 *   état métier et le verdict de la branche réellement prise (invariant I11).
 *
 * Contraintes :
 * - Données pures uniquement, modèle immutable.
 * - Aucun lien avec l'UI, Android Location, ni la persistance.
 * - Aucune décision : le verdict décrit la branche prise, il ne la choisit pas.
 *
 * Principe :
 * - P1.b : le moteur produit les verdicts miroirs de ses branches existantes
 *   (IGNORED_NO_ANCHOR, IGNORED_NOT_RUNNING, REJECTED_STATIONARY,
 *   REJECTED_NOISE, REJECTED_IMPLAUSIBLE_JUMP, ACCEPTED_SEGMENT).
 * - IGNORED_DUPLICATE et IGNORED_NO_SAMPLE sont des verdicts de niveau
 *   runtime (cache de localisation) : jamais produits par le moteur (P1.c).
 */
data class TripProgressResult(
    val state: TripState,
    val verdict: SampleVerdict,
)

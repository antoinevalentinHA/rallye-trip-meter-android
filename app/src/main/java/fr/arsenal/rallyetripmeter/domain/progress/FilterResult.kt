package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — Filter result (P3.a)
 *
 * Rôle :
 * - Porte le résultat d'une application d'échantillon par GpsAccumulationFilter :
 *   le nouvel état métier, le verdict de la branche prise, et l'état de filtre
 *   suivant à transporter.
 *
 * Contraintes :
 * - Données pures uniquement, modèle immutable.
 * - Aucun lien avec l'UI, Android Location, ni la persistance.
 * - Aucune décision : le verdict décrit la branche prise, il ne la choisit pas.
 *
 * Principe :
 * - `state` : nouvel instantané métier (inchangé si l'échantillon est ignoré
 *   ou rejeté).
 * - `verdict` : miroir de la branche réellement prise. Mêmes verdicts que
 *   TripProgressResult (IGNORED_NO_ANCHOR, IGNORED_NOT_RUNNING, REJECTED_STATIONARY,
 *   REJECTED_NOISE, REJECTED_IMPLAUSIBLE_JUMP, ACCEPTED_SEGMENT). IGNORED_DUPLICATE
 *   et IGNORED_NO_SAMPLE restent de niveau runtime et ne sont jamais produits ici.
 * - `nextState` : état de filtre à transporter au tick suivant. En v0 il porte
 *   l'ancre mise à jour. Conformément à la sémantique héritée conservée
 *   volontairement, l'ancre avance vers l'échantillon courant même lorsque le
 *   verdict est un rejet. La correction de ce point appartient à P4.
 */
data class FilterResult(
    val state: TripState,
    val verdict: SampleVerdict,
    val nextState: FilterState,
)

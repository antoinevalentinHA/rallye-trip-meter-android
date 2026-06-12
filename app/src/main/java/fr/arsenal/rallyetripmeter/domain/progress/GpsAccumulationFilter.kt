package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripState

/*
 * ARSENAL RALLYE — GPS accumulation filter contract (P3.a)
 *
 * Rôle :
 * - Définit le contrat d'application d'un échantillon GPS à comportement
 *   constant, en transportant explicitement l'état d'ancre via un FilterState
 *   opaque (la propriété de l'ancre passe du runtime vers le filtre).
 *
 * Contraintes :
 * - Contrat uniquement.
 * - Aucun accès GPS réel, aucun lien Android Location, aucun état mutable,
 *   aucune dépendance UI, aucune persistance.
 * - Aucune nouvelle décision : ce contrat reformule la sémantique héritée
 *   (gardes, ordre, calculs et constantes strictement inchangés).
 *
 * Principe :
 * - Reçoit l'état métier courant, l'état de filtre courant (porteur de l'ancre)
 *   et l'échantillon courant.
 * - Retourne un FilterResult : nouvel état métier, verdict miroir, et l'état de
 *   filtre suivant.
 * - Sémantique d'ancre héritée et conservée volontairement (P3.a et P3.b) :
 *   l'ancre transportée avance vers l'échantillon courant à chaque application,
 *   y compris lorsque l'échantillon est rejeté. La correction de ce point
 *   appartient à P4, pas à ce contrat.
 * - IGNORED_DUPLICATE et IGNORED_NO_SAMPLE restent des verdicts de niveau
 *   runtime : le contrat suppose un échantillon courant exploitable (non nul,
 *   non doublon).
 */
interface GpsAccumulationFilter {
    fun apply(
        tripState: TripState,
        filterState: FilterState,
        currentSample: LocationSample
    ): FilterResult
}

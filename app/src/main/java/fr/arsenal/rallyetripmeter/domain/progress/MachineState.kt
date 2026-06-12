package fr.arsenal.rallyetripmeter.domain.progress

/*
 * ARSENAL RALLYE — Machine state (P4.1)
 *
 * Rôle :
 * - État de la machine d'accumulation : appareil à l'arrêt (STATIONARY) ou en
 *   déplacement (MOVING).
 *
 * Contraintes :
 * - Énumération pure ; aucune logique, aucune dépendance.
 *
 * Principe :
 * - P4.1 : cet état est observé et transporté par FilterState, mais ne gouverne
 *   pas encore l'accumulation (neutre — aucune distance, aucun verdict, aucun
 *   golden ne change). P4.2 l'utilisera pour neutraliser l'accumulation à l'arrêt.
 */
enum class MachineState {
    STATIONARY,
    MOVING,
}

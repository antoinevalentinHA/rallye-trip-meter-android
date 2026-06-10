package fr.arsenal.rallyetripmeter.runtime

import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateStore

/*
 * ARSENAL RALLYE — Process-wide trip runtime holder
 *
 * Rôle :
 * - Fournit une instance unique de TripRuntime à l'échelle du process.
 * - Permet au ViewModel (et, à terme, au foreground service) de partager
 *   le même état autoritaire du trip meter, indépendamment du cycle de vie UI.
 *
 * Contraintes :
 * - Une seule instance par process.
 * - Pur Kotlin : aucun Android, aucune coroutine.
 * - Aucune logique métier ici (déléguée au TripRuntime).
 * - La première construction restaure l'état initial ; les appels suivants
 *   réutilisent l'instance vivante et ignorent tout nouvel initialState,
 *   afin de ne PAS réinitialiser le runtime à chaque recréation du ViewModel.
 *
 * Statut :
 * - B2 : ce palier introduit seulement le holder et son usage par le ViewModel
 *   (via la Factory). Le foreground service ne l'utilise pas encore.
 */
object TripRuntimeHolder {
    @Volatile
    private var runtime: TripRuntime? = null

    fun getRuntime(
        locationEngine: LocationEngine,
        tripStateStore: TripStateStore,
        initialState: TripState
    ): TripRuntime {
        val existing = runtime
        if (existing != null) {
            return existing
        }

        return synchronized(this) {
            val current = runtime
            if (current != null) {
                current
            } else {
                val created = TripRuntime(
                    locationEngine = locationEngine,
                    tripStateStore = tripStateStore,
                    initialState = initialState
                )
                runtime = created
                created
            }
        }
    }
}

package fr.arsenal.rallyetripmeter.domain.persistence

/*
 * ARSENAL RALLYE — No-op trip state store
 *
 * Rôle :
 * - Implémentation neutre par défaut : ne persiste rien, ne restaure rien.
 *
 * Contraintes :
 * - Aucun effet de bord.
 * - Sert de défaut tant qu'aucun stockage réel n'est câblé.
 */
class NoOpTripStateStore : TripStateStore {
    override fun save(snapshot: TripStateSnapshot) {
        // Implémentation neutre : aucun stockage.
    }

    override fun load(): TripStateSnapshot? {
        return null
    }
}

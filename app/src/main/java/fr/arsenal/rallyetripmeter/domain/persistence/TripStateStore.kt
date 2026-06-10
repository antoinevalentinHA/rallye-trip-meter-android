package fr.arsenal.rallyetripmeter.domain.persistence

/*
 * ARSENAL RALLYE — Trip state store port
 *
 * Rôle :
 * - Port pur de persistance de l'état durable du trip meter.
 *
 * Contraintes :
 * - Aucun détail de stockage (Android, SharedPreferences, fichier, etc.).
 * - Les implémentations concrètes vivent hors du domaine.
 */
interface TripStateStore {
    fun save(snapshot: TripStateSnapshot)

    fun load(): TripStateSnapshot?
}

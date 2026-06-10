package fr.arsenal.rallyetripmeter.android.location

import android.content.Context

/*
 * ARSENAL RALLYE — Process-wide location engine holder
 *
 * Rôle :
 * - Fournit une instance unique de AndroidLocationEngine à l'échelle du process.
 * - Permet au ViewModel (lecture) et, à terme, au foreground service (pilotage)
 *   de partager le même abonnement GPS, donc d'éviter toute double acquisition.
 *
 * Contraintes :
 * - Une seule instance par process (un seul requestLocationUpdates possible).
 * - Lié au Context applicatif (jamais à une Activity).
 * - Aucune logique métier, aucune accumulation, aucune persistance.
 *
 * Statut :
 * - Ce palier introduit seulement le holder ; le comportement reste inchangé.
 * - L'acquisition est encore démarrée / arrêtée par la Route via le ViewModel.
 * - Le pilotage par le foreground service viendra dans des sous-paliers ultérieurs.
 */
object LocationEngineHolder {
    @Volatile
    private var engine: AndroidLocationEngine? = null

    fun getEngine(context: Context): AndroidLocationEngine {
        val existing = engine
        if (existing != null) {
            return existing
        }

        return synchronized(this) {
            val current = engine
            if (current != null) {
                current
            } else {
                val created = AndroidLocationEngine(
                    context = context.applicationContext
                )
                engine = created
                created
            }
        }
    }
}

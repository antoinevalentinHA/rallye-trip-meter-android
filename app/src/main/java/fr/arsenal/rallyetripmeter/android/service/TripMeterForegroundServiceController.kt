package fr.arsenal.rallyetripmeter.android.service

import android.content.Context
import android.content.Intent

/*
 * ARSENAL RALLYE — Trip meter foreground service controller
 *
 * Rôle :
 * - Démarre / arrête proprement le TripMeterForegroundService depuis le code applicatif.
 *
 * Contraintes :
 * - Frontière Android : aucune logique métier.
 * - Démarrage via startForegroundService (le service appelle ensuite startForeground).
 */
class TripMeterForegroundServiceController(
    context: Context
) {
    private val applicationContext: Context = context.applicationContext

    fun start() {
        applicationContext.startForegroundService(serviceIntent())
    }

    fun stop() {
        applicationContext.stopService(serviceIntent())
    }

    private fun serviceIntent(): Intent {
        return Intent(applicationContext, TripMeterForegroundService::class.java)
    }
}

package fr.arsenal.rallyetripmeter.android.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/*
 * ARSENAL RALLYE — Trip meter foreground service (coquille)
 *
 * Rôle :
 * - Socle déclaratif du futur foreground service d'acquisition GPS.
 *
 * Statut :
 * - Coquille volontairement inerte : ne démarre rien.
 * - Pas de startForeground, pas de notification, pas d'acquisition GPS.
 * - Pas de modification de l'état du tripmeter ni de la persistance.
 * - Aucun pilotage depuis la Route ni le ViewModel.
 * - Le comportement réel (acquisition, notification, accumulation) viendra
 *   dans des paliers ultérieurs.
 */
class TripMeterForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

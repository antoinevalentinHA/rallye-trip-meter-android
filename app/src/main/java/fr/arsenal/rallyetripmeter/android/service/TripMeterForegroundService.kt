package fr.arsenal.rallyetripmeter.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/*
 * ARSENAL RALLYE — Trip meter foreground service
 *
 * Rôle :
 * - Service au premier plan signalant une session active via une notification ongoing.
 *
 * Statut :
 * - Démarre / arrête réellement le premier plan (startForeground / stopForeground).
 * - N'acquiert PAS encore le GPS (aucun requestLocationUpdates) : pas de double acquisition.
 * - Ne modifie ni l'état du tripmeter ni la persistance.
 * - Acquisition GPS, propriété unique de l'acquisition et accumulation : paliers ultérieurs.
 */
class TripMeterForegroundService : Service() {
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        TripMeterServiceNotification.ensureChannel(this)

        val notification = TripMeterServiceNotification.build(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TripMeterServiceNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(
                TripMeterServiceNotification.NOTIFICATION_ID,
                notification
            )
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

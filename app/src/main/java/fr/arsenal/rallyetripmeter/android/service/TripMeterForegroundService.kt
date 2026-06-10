package fr.arsenal.rallyetripmeter.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import fr.arsenal.rallyetripmeter.android.location.LocationEngineHolder

/*
 * ARSENAL RALLYE — Trip meter foreground service
 *
 * Rôle :
 * - Service au premier plan signalant une session active via une notification ongoing.
 *
 * Statut :
 * - Démarre / arrête réellement le premier plan (startForeground / stopForeground).
 * - Démarre / arrête l'acquisition GPS via le LocationEngineHolder partagé (instance unique).
 * - Ne modifie ni l'état du tripmeter ni la persistance ; n'accumule pas.
 * - La Route / le ViewModel pilotent encore le même moteur en parallèle (retrait en GPS-OWN-4).
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

        LocationEngineHolder.getEngine(applicationContext).start()

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        LocationEngineHolder.getEngine(applicationContext).stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

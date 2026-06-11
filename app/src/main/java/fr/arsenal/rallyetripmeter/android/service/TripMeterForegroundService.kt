package fr.arsenal.rallyetripmeter.android.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import fr.arsenal.rallyetripmeter.android.diag.TickLogSessionFactory
import fr.arsenal.rallyetripmeter.android.diag.TickLogSinkHolder
import fr.arsenal.rallyetripmeter.android.location.LocationEngineHolder
import fr.arsenal.rallyetripmeter.android.persistence.SharedPreferencesTripStateStore
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.persistence.toTripState
import fr.arsenal.rallyetripmeter.runtime.TripRuntime
import fr.arsenal.rallyetripmeter.runtime.TripRuntimeHolder
import fr.arsenal.rallyetripmeter.runtime.TripRuntimeEvent

/*
 * ARSENAL RALLYE — Trip meter foreground service
 *
 * Rôle :
 * - Service au premier plan signalant une session active via une notification ongoing.
 * - Propriétaire de l'acquisition GPS (LocationEngineHolder partagé).
 * - Pilote l'accumulation pendant que le service tourne, en déléguant au TripRuntime
 *   process-wide partagé (TripRuntimeHolder) — y compris écran éteint / arrière-plan.
 *
 * Contraintes :
 * - Ne possède aucun état métier : le TripRuntime reste seule autorité du TripState.
 * - N'écrit jamais directement dans le store : la persistance passe par le runtime (throttle).
 * - Boucle d'échantillonnage postée sur le main Looper : sérialisée avec le pump UI
 *   (Dispatchers.Main) sur le même thread, donc aucune course ni double accumulation
 *   (un seul previousLocationSample partagé pave le trajet une seule fois).
 *
 * Statut :
 * - B3 : la boucle service alimente le runtime. Le pump UI reste actif (retrait en B4).
 */
class TripMeterForegroundService : Service() {
    private val sampleHandler = Handler(Looper.getMainLooper())

    private var runtime: TripRuntime? = null

    private val sampleTick = object : Runnable {
        override fun run() {
            runtime?.onEvent(TripRuntimeEvent.ApplyLocationSample)
            sampleHandler.postDelayed(this, SAMPLE_INTERVAL_MS)
        }
    }

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

        if (!TickLogSinkHolder.hasSessionSink()) {
            TickLogSessionFactory.createSessionSink(applicationContext)?.let { sink ->
                TickLogSinkHolder.installSessionSink(sink)
            }
        }

        runtime = resolveRuntime()
        sampleHandler.removeCallbacks(sampleTick)
        sampleHandler.post(sampleTick)

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        sampleHandler.removeCallbacks(sampleTick)
        runtime = null
        TickLogSinkHolder.clearSessionSink()?.close()
        LocationEngineHolder.getEngine(applicationContext).stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun resolveRuntime(): TripRuntime {
        val locationEngine = LocationEngineHolder.getEngine(applicationContext)
        val tripStateStore = SharedPreferencesTripStateStore(applicationContext)

        val initialState = tripStateStore.load()?.toTripState()
            ?: TripState(gpsStatus = locationEngine.getGpsStatus())

        return TripRuntimeHolder.getRuntime(
            locationEngine = locationEngine,
            tripStateStore = tripStateStore,
            initialState = initialState,
            tickLogSink = TickLogSinkHolder.getSink()
        )
    }

    private companion object {
        private const val SAMPLE_INTERVAL_MS = 1_000L
    }
}

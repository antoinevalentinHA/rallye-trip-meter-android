package fr.arsenal.rallyetripmeter.android.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import fr.arsenal.rallyetripmeter.MainActivity
import fr.arsenal.rallyetripmeter.R

/*
 * ARSENAL RALLYE — Foreground service notification
 *
 * Rôle :
 * - Prépare le canal et la notification ongoing du futur foreground service.
 *
 * Statut :
 * - Brique préparatoire : non encore utilisée (pas de startForeground à ce palier).
 * - minSdk 28 : NotificationChannel toujours disponible, aucun garde de version requis.
 * - Importance basse (IMPORTANCE_LOW) : notification visible mais non intrusive.
 */
object TripMeterServiceNotification {
    const val NOTIFICATION_ID = 1001

    private const val CHANNEL_ID = "trip_meter_foreground_service"
    private const val CHANNEL_NAME = "Acquisition GPS"
    private const val CONTENT_TITLE = "Rallye Trip Meter actif"
    private const val CONTENT_TEXT = "Service GPS prêt"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW
        )

        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    fun build(context: Context): Notification {
        return Notification.Builder(context, CHANNEL_ID)
            .setContentTitle(CONTENT_TITLE)
            .setContentText(CONTENT_TEXT)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun openAppIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )
    }
}

package fr.arsenal.rallyetripmeter.ui.mapper

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.UiGpsStatus
import fr.arsenal.rallyetripmeter.ui.model.UiSessionStatus
import java.util.Locale

/*
 * ARSENAL RALLYE — UI mapper
 *
 * Rôle :
 * - Convertit l'état métier TripState vers l'état d'affichage TripDisplayState.
 *
 * Contraintes :
 * - Aucun accès GPS.
 * - Aucun contrôleur.
 * - Aucune mutation métier.
 * - Aucun calcul de distance.
 * - Aucun état Android.
 */
fun TripState.toTripDisplayState(): TripDisplayState {
    return TripDisplayState(
        partialDistanceText = partialDistanceMeters.toKilometerText(),
        totalDistanceText = totalDistanceMeters.toKilometerText(),
        speedText = "0 km/h",
        gpsStatus = gpsStatus.toUiGpsStatus(),
        gpsAccuracyText = null,
        sessionStatus = sessionState.toUiSessionStatus()
    )
}

private fun Double.toKilometerText(): String {
    return String.format(
        Locale.US,
        "%.2f km",
        this / 1000.0
    )
}

private fun GpsStatus.toUiGpsStatus(): UiGpsStatus {
    return when (this) {
        GpsStatus.Unavailable -> UiGpsStatus.Unknown
        GpsStatus.Searching -> UiGpsStatus.Searching
        GpsStatus.Fixed -> UiGpsStatus.Ok
    }
}

private fun TripSessionState.toUiSessionStatus(): UiSessionStatus {
    return when (this) {
        TripSessionState.Stopped -> UiSessionStatus.Stopped
        TripSessionState.Running -> UiSessionStatus.Active
        TripSessionState.Paused -> UiSessionStatus.Paused
    }
}

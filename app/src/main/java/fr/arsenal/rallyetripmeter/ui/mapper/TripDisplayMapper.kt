package fr.arsenal.rallyetripmeter.ui.mapper

import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.model.TripDisplayState
import fr.arsenal.rallyetripmeter.ui.model.UiGpsStatus
import fr.arsenal.rallyetripmeter.ui.model.UiSessionStatus
import java.util.Locale
import kotlin.math.roundToInt

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
fun TripState.toTripDisplayState(
    calibrationCoefficient: CalibrationCoefficient = CalibrationCoefficient.default()
): TripDisplayState {
    // Calibration manuelle appliquée à l'affichage des distances uniquement (partiel + total).
    // Distances brutes (TripState) intactes ; 1.000 = identité.
    val coefficient = calibrationCoefficient.toDouble()
    return TripDisplayState(
        partialDistanceText = (partialDistanceMeters * coefficient).toKilometerText(),
        totalDistanceText = (totalDistanceMeters * coefficient).toKilometerText(),
        speedText = speedMetersPerSecond.toSpeedText(),
        gpsStatus = gpsStatus.toUiGpsStatus(),
        gpsAccuracyText = accuracyMeters.toAccuracyText(),
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

private fun Double?.toAccuracyText(): String? {
    if (this == null) {
        return null
    }

    return "±${roundToInt()} m"
}

private fun Double?.toSpeedText(): String {
    if (this == null) {
        return "—"
    }

    val speedKmh = (this * 3.6).roundToInt()
    return "$speedKmh km/h"
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

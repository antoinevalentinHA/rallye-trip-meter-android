package fr.arsenal.rallyetripmeter.ui.model

data class TripDisplayState(
    val partialDistanceText: String,
    val totalDistanceText: String,
    val speedText: String,
    val gpsStatus: UiGpsStatus,
    val gpsAccuracyText: String?,
    val sessionStatus: UiSessionStatus,
    val locationPermissionStatus: UiLocationPermissionStatus = UiLocationPermissionStatus.Unknown,
    val calibrationText: String = "1.000"
) {
    val gpsStatusText: String
        get() = gpsStatus.label

    val sessionStatusText: String
        get() = sessionStatus.label

    val locationPermissionStatusText: String
        get() = locationPermissionStatus.label

    val sessionActionText: String
        get() {
            return when (sessionStatus) {
                UiSessionStatus.Stopped -> "START"
                UiSessionStatus.Active -> "PAUSE"
                UiSessionStatus.Paused -> "REPRISE"
            }
        }

    val isStopEnabled: Boolean
        get() = sessionStatus != UiSessionStatus.Stopped

    val arePartialControlsEnabled: Boolean
        get() = sessionStatus != UiSessionStatus.Stopped

    companion object {
        fun preview(): TripDisplayState {
            return TripDisplayState(
                partialDistanceText = "0.80 km",
                totalDistanceText = "124.37 km",
                speedText = "76 km/h",
                gpsStatus = UiGpsStatus.Ok,
                gpsAccuracyText = "±4 m",
                sessionStatus = UiSessionStatus.Active,
                locationPermissionStatus = UiLocationPermissionStatus.Granted
            )
        }
    }
}

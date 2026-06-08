package fr.arsenal.rallyetripmeter.ui.model

data class TripDisplayState(
    val partialDistanceText: String,
    val totalDistanceText: String,
    val speedText: String,
    val gpsStatus: UiGpsStatus,
    val gpsAccuracyText: String?,
    val sessionStatus: UiSessionStatus
) {
    val gpsStatusText: String
        get() {
            return if (gpsAccuracyText == null) {
                gpsStatus.label
            } else {
                "${gpsStatus.label} $gpsAccuracyText"
            }
        }

    val sessionStatusText: String
        get() = sessionStatus.label

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

    companion object {
        fun preview(): TripDisplayState {
            return TripDisplayState(
                partialDistanceText = "0.80 km",
                totalDistanceText = "124.37 km",
                speedText = "76 km/h",
                gpsStatus = UiGpsStatus.Ok,
                gpsAccuracyText = "±4 m",
                sessionStatus = UiSessionStatus.Active
            )
        }
    }
}

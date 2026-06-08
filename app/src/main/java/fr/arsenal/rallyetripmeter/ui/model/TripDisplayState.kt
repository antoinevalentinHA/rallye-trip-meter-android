package fr.arsenal.rallyetripmeter.ui.model

data class TripDisplayState(
    val partialDistanceText: String,
    val totalDistanceText: String,
    val speedText: String,
    val gpsStatusText: String,
    val sessionStatusText: String
) {
    companion object {
        fun preview(): TripDisplayState {
            return TripDisplayState(
                partialDistanceText = "0.80 km",
                totalDistanceText = "124.37 km",
                speedText = "76 km/h",
                gpsStatusText = "GPS OK ±4 m",
                sessionStatusText = "ACTIF"
            )
        }
    }
}
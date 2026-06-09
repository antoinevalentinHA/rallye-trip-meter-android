package fr.arsenal.rallyetripmeter.ui.model

enum class UiSessionStatus(
    val label: String
) {
    Stopped(label = "ARRÊTÉ"),
    Active(label = "ACTIF"),
    Paused(label = "PAUSE")
}

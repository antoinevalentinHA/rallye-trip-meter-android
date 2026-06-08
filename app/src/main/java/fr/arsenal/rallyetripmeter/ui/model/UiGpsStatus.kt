package fr.arsenal.rallyetripmeter.ui.model

enum class UiGpsStatus(
    val label: String
) {
    Unknown(label = "GPS ?"),
    Searching(label = "GPS RECHERCHE"),
    Ok(label = "GPS OK"),
    Degraded(label = "GPS DÉGRADÉ"),
    Lost(label = "GPS PERDU"),
    Invalid(label = "GPS INVALIDE")
}

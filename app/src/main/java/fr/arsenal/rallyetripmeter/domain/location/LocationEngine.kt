package fr.arsenal.rallyetripmeter.domain.location

import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus

/*
 * ARSENAL RALLYE — Location engine contract
 *
 * Rôle :
 * - Définit le contrat d'une source de localisation exploitable par le domaine.
 *
 * Contraintes :
 * - Contrat uniquement.
 * - Aucun accès GPS réel.
 * - Aucun lien avec Android Location.
 * - Aucune dépendance UI.
 * - Aucune persistance.
 *
 * Principe :
 * - Le moteur expose son statut GPS métier.
 * - Le moteur expose le dernier échantillon de localisation disponible.
 */
interface LocationEngine {
    fun getGpsStatus(): GpsStatus

    fun getLastLocationSample(): LocationSample?
}

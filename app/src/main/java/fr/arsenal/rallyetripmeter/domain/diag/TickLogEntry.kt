package fr.arsenal.rallyetripmeter.domain.diag

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState

/*
 * ARSENAL RALLYE — Tick log entry model
 *
 * Rôle :
 * - Représente une ligne d'observabilité du pipeline GPS : un tick = une entrée.
 * - TickLogMeta représente la ligne d'en-tête d'un fichier de session.
 *
 * Contraintes :
 * - Données pures uniquement, modèle immutable.
 * - Aucun lien avec l'UI, Android Location, ni la persistance.
 * - Aucun calcul : les valeurs sont fournies par l'émetteur, jamais dérivées ici.
 *
 * Principe :
 * - Schéma plat (aucune imbrication) pour rester trivialement sérialisable
 *   en JSONL sans dépendance.
 * - Les champs absents au tick (pas d'échantillon, pas de précédent, gardes
 *   non évaluées) sont null et sérialisés comme tels.
 */
data class TickLogEntry(
    /** Horloge monotone de l'app au tick, en millisecondes écoulées. */
    val tickElapsedMillis: Long,
    /** Timestamp epoch de l'échantillon GPS courant, si présent. */
    val sampleTimestampMillis: Long?,
    /** Vrai si l'échantillon diffère du précédent (nouveau fix), si évaluable. */
    val sampleIsNew: Boolean?,
    val latitude: Double?,
    val longitude: Double?,
    val accuracyMeters: Double?,
    val speedMetersPerSecond: Double?,
    val gpsStatus: GpsStatus,
    val sessionState: TripSessionState,
    /** Timestamp epoch de l'échantillon de référence (précédent), si présent. */
    val previousTimestampMillis: Long?,
    /** Distance brute du segment évalué, en mètres, si calculée. */
    val segmentMeters: Double?,
    val verdict: SampleVerdict,
    /** Plancher de mouvement appliqué au tick, en mètres, si évalué. */
    val floorMeters: Double?,
    /** Vitesse implicite du segment, en km/h, si évaluée. */
    val impliedSpeedKmh: Double?,
    /** Distance ajoutée au total par ce tick, en mètres (0.0 si rien d'accumulé). */
    val deltaTotalMeters: Double,
    /** Distance totale brute après ce tick, en mètres. */
    val totalMeters: Double,
)

/*
 * Ligne d'en-tête d'un fichier de log de session.
 */
data class TickLogMeta(
    /** Hash du commit de build, ou identifiant de version équivalent. */
    val commitHash: String,
    /** Identifiant lisible de l'appareil (modèle). */
    val device: String,
    /** Timestamp epoch du démarrage de la session de log, en millisecondes. */
    val startedAtMillis: Long,
)

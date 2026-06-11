package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.runtime.TripRuntime
import fr.arsenal.rallyetripmeter.runtime.TripRuntimeEvent

/*
 * ARSENAL RALLYE — Tick log pipeline replay (outillage P2.a, sources de test)
 *
 * Rôle :
 * - Ré-exécute un log P1 dans le pipeline réel : les échantillons sont
 *   reconstruits depuis les lignes et réinjectés dans un TripRuntime neuf
 *   (moteur réel, mêmes gardes). Prouve la fidélité log <-> pipeline et
 *   servira de socle au replay bit-à-bit de P3.
 *
 * Contraintes :
 * - Pur Kotlin/JVM ; consomme le runtime et le moteur en lecture, sans les
 *   modifier.
 * - Déterministe : un tick rejoué par entrée, dans l'ordre du fichier.
 *
 * Limites assumées :
 * - La session est forcée Running (cas des logs de session réels) : les
 *   entrées IGNORED_NOT_RUNNING d'un log mixte divergeraient — la
 *   comparaison le rendrait visible, c'est voulu.
 * - L'altitude n'est pas journalisée (le moteur l'ignore) ; le coefficient
 *   moteur vaut 1.0 comme en production.
 */
object TickLogPipelineReplay {
    data class PipelineReplayResult(
        val replayedTotalMeters: Double,
        val replayedVerdictCounts: Map<SampleVerdict, Int>,
    )

    data class FidelityComparison(
        val recordedTotalMeters: Double,
        val replayedTotalMeters: Double,
        val recordedVerdictCounts: Map<SampleVerdict, Int>,
        val replayedVerdictCounts: Map<SampleVerdict, Int>,
    ) {
        val totalsMatch: Boolean
            get() = kotlin.math.abs(recordedTotalMeters - replayedTotalMeters) <
                TOTAL_TOLERANCE_METERS

        val verdictsMatch: Boolean
            get() = recordedVerdictCounts == replayedVerdictCounts

        private companion object {
            const val TOTAL_TOLERANCE_METERS = 1.0E-6
        }
    }

    fun replay(parsed: TickLogReplayReader.ParsedTickLog): PipelineReplayResult {
        val scriptedEngine = ScriptedLocationEngine(
            ticks = parsed.entries.map { indexed -> indexed.entry.toScriptedTick() }
        )
        val recordingSink = VerdictRecordingSink()

        val runtime = TripRuntime(
            locationEngine = scriptedEngine,
            tickLogSink = recordingSink,
            nowMillis = { 0L },
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        repeat(parsed.entries.size) {
            scriptedEngine.advance()
            runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        }

        return PipelineReplayResult(
            replayedTotalMeters = runtime.state.totalDistanceMeters,
            replayedVerdictCounts = recordingSink.verdictCounts()
        )
    }

    fun compare(
        report: TickLogReplayAnalyzer.TickLogReplayReport,
        replayed: PipelineReplayResult
    ): FidelityComparison {
        return FidelityComparison(
            recordedTotalMeters = report.totalAccumulatedMeters,
            replayedTotalMeters = replayed.replayedTotalMeters,
            recordedVerdictCounts = report.verdictCounts,
            replayedVerdictCounts = replayed.replayedVerdictCounts
        )
    }

    private data class ScriptedTick(
        val sample: LocationSample?,
        val gpsStatus: GpsStatus,
    )

    private fun TickLogEntry.toScriptedTick(): ScriptedTick {
        val latitude = this.latitude
        val longitude = this.longitude

        val sample = if (latitude == null || longitude == null) {
            null
        } else {
            LocationSample(
                point = GeoPoint(
                    latitude = latitude,
                    longitude = longitude,
                    timestampMillis = sampleTimestampMillis
                ),
                accuracyMeters = accuracyMeters,
                speedMetersPerSecond = speedMetersPerSecond
            )
        }

        return ScriptedTick(
            sample = sample,
            gpsStatus = gpsStatus
        )
    }

    /*
     * LocationEngine scripté : reproduit la sémantique du cache réel —
     * getLastLocationSample() retourne l'échantillon du tick courant,
     * avancé explicitement avant chaque ApplyLocationSample.
     */
    private class ScriptedLocationEngine(
        private val ticks: List<ScriptedTick>
    ) : LocationEngine {
        private var index = -1

        fun advance() {
            if (index < ticks.size - 1) {
                index += 1
            }
        }

        override fun getGpsStatus(): GpsStatus {
            return ticks.getOrNull(index)?.gpsStatus ?: GpsStatus.Unavailable
        }

        override fun getLastLocationSample(): LocationSample? {
            return ticks.getOrNull(index)?.sample
        }
    }

    private class VerdictRecordingSink : TickLogSink {
        private val verdicts = mutableListOf<SampleVerdict>()

        override fun logMeta(meta: TickLogMeta) {
            // Sans objet en replay.
        }

        override fun log(entry: TickLogEntry) {
            verdicts.add(entry.verdict)
        }

        override fun flush() {
            // Sans objet en replay.
        }

        fun verdictCounts(): Map<SampleVerdict, Int> {
            val counts = SampleVerdict.entries.associateWith { 0 }.toMutableMap()
            for (verdict in verdicts) {
                counts[verdict] = (counts[verdict] ?: 0) + 1
            }
            return counts
        }
    }
}

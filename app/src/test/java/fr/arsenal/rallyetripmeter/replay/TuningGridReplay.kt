package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink
import fr.arsenal.rallyetripmeter.domain.distance.HaversineDistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.progress.DistanceTripProgressEngine
import fr.arsenal.rallyetripmeter.domain.progress.FilterTuning
import fr.arsenal.rallyetripmeter.runtime.TripRuntime
import fr.arsenal.rallyetripmeter.runtime.TripRuntimeEvent

/*
 * ARSENAL RALLYE — Tuning grid replay (outillage P5.a, sources de test)
 *
 * Rôle :
 * - Rejouer un log P1 du corpus à travers le pipeline réel pour un FilterTuning
 *   DONNÉ, et restituer le total accumulé + la répartition des verdicts. En
 *   balayant plusieurs tuples, on obtient une « grille » (tuple × log) qui sert de
 *   socle de mesure à P5 (calage). P5.a se limite à l'outillage et à la BASELINE
 *   du tuple par défaut ; aucun tuple candidat n'est choisi ici.
 *
 * Contraintes (P5.a) :
 * - Pur Kotlin/JVM, code de TEST uniquement. Ne modifie ni le moteur, ni
 *   FilterTuning, ni aucune constante de production.
 * - Le tuning est INJECTÉ dans un moteur éphémère (`DistanceTripProgressEngine`
 *   construit avec le tuple voulu) puis transporté par un `TripRuntime` neuf. Le
 *   défaut de production (`FilterTuning()`) reste la référence et n'est jamais
 *   réécrit.
 * - Le chemin de replay reproduit fidèlement `TickLogPipelineReplay` (session
 *   forcée Running, horloge à 0, un tick par entrée). Au tuple par défaut, la
 *   grille DOIT donc reproduire le pipeline existant — propriété vérifiée par le
 *   self-test.
 *
 * Hors périmètre P5.a (donc absent ici) : choix d'un tuple, modification d'un
 * défaut, analyse de sensibilité décisionnelle (P5.b), validation marche (P5.c).
 */
object TuningGridReplay {

    /** Un tuple de tuning nommé, pour étiqueter les lignes de la grille. */
    data class NamedTuning(
        val label: String,
        val tuning: FilterTuning,
    )

    /** Résultat d'un replay (un log, un tuple). */
    data class GridCell(
        val tuningLabel: String,
        val logName: String,
        val tickCount: Int,
        val totalMeters: Double,
        val verdictCounts: Map<SampleVerdict, Int>,
    ) {
        /** Invariant structurel : chaque tick exploitable porte un verdict. */
        val verdictSumMatchesTicks: Boolean
            get() = verdictCounts.values.sum() == tickCount
    }

    /** Rejoue un log pour un tuning donné. */
    fun replay(
        parsed: TickLogReplayReader.ParsedTickLog,
        tuning: FilterTuning,
        tuningLabel: String,
        logName: String,
    ): GridCell {
        val scriptedEngine = ScriptedLocationEngine(
            ticks = parsed.entries.map { indexed -> indexed.entry.toScriptedTick() }
        )
        val recordingSink = VerdictRecordingSink()

        val runtime = TripRuntime(
            gpsAccumulationFilter = DistanceTripProgressEngine(
                distanceEngine = HaversineDistanceEngine(),
                tuning = tuning
            ),
            locationEngine = scriptedEngine,
            tickLogSink = recordingSink,
            nowMillis = { 0L },
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        repeat(parsed.entries.size) {
            scriptedEngine.advance()
            runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        }

        return GridCell(
            tuningLabel = tuningLabel,
            logName = logName,
            tickCount = parsed.entries.size,
            totalMeters = runtime.state.totalDistanceMeters,
            verdictCounts = recordingSink.verdictCounts()
        )
    }

    /** Rejoue tout un corpus pour un ensemble de tuples : la grille complète. */
    fun runGrid(
        corpus: List<Pair<String, TickLogReplayReader.ParsedTickLog>>,
        tunings: List<NamedTuning>,
    ): List<GridCell> {
        val cells = mutableListOf<GridCell>()
        for (named in tunings) {
            for ((logName, parsed) in corpus) {
                cells += replay(parsed, named.tuning, named.label, logName)
            }
        }
        return cells
    }

    /** Rendu Markdown lisible d'une liste de cellules (baseline ou grille). */
    fun renderTable(cells: List<GridCell>): String {
        val builder = StringBuilder()
        builder.appendLine("| tuple | log | ticks | total_m | accepted | rej_stat | rej_noise | rej_jump | ign |")
        builder.appendLine("|---|---|---:|---:|---:|---:|---:|---:|---:|")
        for (cell in cells) {
            val v = cell.verdictCounts
            val accepted = v[SampleVerdict.ACCEPTED_SEGMENT] ?: 0
            val rejStat = v[SampleVerdict.REJECTED_STATIONARY] ?: 0
            val rejNoise = v[SampleVerdict.REJECTED_NOISE] ?: 0
            val rejJump = v[SampleVerdict.REJECTED_IMPLAUSIBLE_JUMP] ?: 0
            val ignored = (v[SampleVerdict.IGNORED_NO_ANCHOR] ?: 0) +
                (v[SampleVerdict.IGNORED_NOT_RUNNING] ?: 0) +
                (v[SampleVerdict.IGNORED_DUPLICATE] ?: 0) +
                (v[SampleVerdict.IGNORED_NO_SAMPLE] ?: 0)
            builder.appendLine(
                "| ${cell.tuningLabel} | ${cell.logName} | ${cell.tickCount} | " +
                    "${"%.1f".format(cell.totalMeters)} | $accepted | $rejStat | " +
                    "$rejNoise | $rejJump | $ignored |"
            )
        }
        return builder.toString()
    }

    // --- Replicas locaux minimaux (le harness de fidélité P2 reste intact) ---

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
        return ScriptedTick(sample = sample, gpsStatus = gpsStatus)
    }

    private class ScriptedLocationEngine(
        private val ticks: List<ScriptedTick>
    ) : LocationEngine {
        private var index = -1

        fun advance() {
            if (index < ticks.size - 1) index += 1
        }

        override fun getGpsStatus(): GpsStatus =
            ticks.getOrNull(index)?.gpsStatus ?: GpsStatus.Unavailable

        override fun getLastLocationSample(): LocationSample? =
            ticks.getOrNull(index)?.sample
    }

    private class VerdictRecordingSink : TickLogSink {
        private val verdicts = mutableListOf<SampleVerdict>()

        override fun logMeta(meta: TickLogMeta) {}

        override fun log(entry: TickLogEntry) {
            verdicts.add(entry.verdict)
        }

        override fun flush() {}

        fun verdictCounts(): Map<SampleVerdict, Int> {
            val counts = SampleVerdict.entries.associateWith { 0 }.toMutableMap()
            for (verdict in verdicts) {
                counts[verdict] = (counts[verdict] ?: 0) + 1
            }
            return counts
        }
    }
}

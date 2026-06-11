package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import java.util.Locale

/*
 * ARSENAL RALLYE — Tick log replay report (outillage P2.a, sources de test)
 *
 * Rôle :
 * - Calcule le rapport déterministe d'un log P1 : comptages par verdict,
 *   distance accumulée, statistiques accuracy/vitesse, segments acceptés,
 *   et la métrique d'hypothèse "vitesse parasite >= 0,5 m/s".
 *
 * Contraintes :
 * - Pur Kotlin/JVM, aucune dépendance Android ni JSON.
 * - Aucune décision : ce rapport décrit ce que le pipeline a fait,
 *   il ne re-filtre rien.
 * - Déterministe : ordre du fichier préservé, énumérations triées par ordinal.
 *
 * Principe :
 * - "Distance accumulée" = somme des delta_total_m des verdicts acceptés
 *   (recoupée avec le total_m de la dernière entrée, rapporté séparément).
 * - Le seuil 0,5 m/s reflète STATIONARY_SPEED_MPS du moteur (constante privée,
 *   dupliquée ici volontairement : l'analyse ne dépend pas des internes).
 */
object TickLogReplayAnalyzer {
    /** Miroir analytique de DistanceTripProgressEngine.STATIONARY_SPEED_MPS. */
    const val SPEED_HYPOTHESIS_THRESHOLD_MPS: Double = 0.5

    data class AcceptedSegment(
        val lineNumber: Int,
        val tickElapsedMillis: Long,
        val sampleTimestampMillis: Long?,
        val deltaTotalMeters: Double,
        val accuracyMeters: Double?,
        val speedMetersPerSecond: Double?,
        val totalMetersAfter: Double,
    )

    data class ValueStats(
        val count: Int,
        val min: Double,
        val max: Double,
        val mean: Double,
    )

    data class TickLogReplayReport(
        val meta: TickLogMeta?,
        val tickCount: Int,
        val malformedLineCount: Int,
        val verdictCounts: Map<SampleVerdict, Int>,
        val acceptedSegmentCount: Int,
        val totalAccumulatedMeters: Double,
        val finalTotalMeters: Double?,
        val meanAcceptedSegmentMeters: Double?,
        val accuracyStats: ValueStats?,
        val speedStats: ValueStats?,
        val acceptedSegments: List<AcceptedSegment>,
        val acceptedSegmentsWithSpeedGe05: Int,
        val distanceFromSegmentsWithSpeedGe05: Double,
    )

    fun analyze(parsed: TickLogReplayReader.ParsedTickLog): TickLogReplayReport {
        val verdictCounts = SampleVerdict.entries.associateWith { 0 }.toMutableMap()
        val acceptedSegments = mutableListOf<AcceptedSegment>()
        val accuracies = mutableListOf<Double>()
        val speeds = mutableListOf<Double>()
        var totalAccumulated = 0.0

        for (indexed in parsed.entries) {
            val entry = indexed.entry

            verdictCounts[entry.verdict] = (verdictCounts[entry.verdict] ?: 0) + 1
            entry.accuracyMeters?.let(accuracies::add)
            entry.speedMetersPerSecond?.let(speeds::add)

            if (entry.verdict == SampleVerdict.ACCEPTED_SEGMENT) {
                totalAccumulated += entry.deltaTotalMeters
                acceptedSegments.add(
                    AcceptedSegment(
                        lineNumber = indexed.lineNumber,
                        tickElapsedMillis = entry.tickElapsedMillis,
                        sampleTimestampMillis = entry.sampleTimestampMillis,
                        deltaTotalMeters = entry.deltaTotalMeters,
                        accuracyMeters = entry.accuracyMeters,
                        speedMetersPerSecond = entry.speedMetersPerSecond,
                        totalMetersAfter = entry.totalMeters
                    )
                )
            }
        }

        val suspectSegments = acceptedSegments.filter { segment ->
            val speed = segment.speedMetersPerSecond
            speed != null && speed >= SPEED_HYPOTHESIS_THRESHOLD_MPS
        }

        return TickLogReplayReport(
            meta = parsed.meta,
            tickCount = parsed.tickCount,
            malformedLineCount = parsed.malformedLines.size,
            verdictCounts = verdictCounts,
            acceptedSegmentCount = acceptedSegments.size,
            totalAccumulatedMeters = totalAccumulated,
            finalTotalMeters = parsed.entries.lastOrNull()?.entry?.totalMeters,
            meanAcceptedSegmentMeters = if (acceptedSegments.isEmpty()) {
                null
            } else {
                totalAccumulated / acceptedSegments.size
            },
            accuracyStats = statsOf(accuracies),
            speedStats = statsOf(speeds),
            acceptedSegments = acceptedSegments,
            acceptedSegmentsWithSpeedGe05 = suspectSegments.size,
            distanceFromSegmentsWithSpeedGe05 = suspectSegments.sumOf {
                it.deltaTotalMeters
            }
        )
    }

    fun renderText(report: TickLogReplayReport): String {
        val builder = StringBuilder()

        builder.appendLine("=== RAPPORT DE REPLAY — LOG P1 (schéma v1) ===")
        report.meta?.let { meta ->
            builder.appendLine(
                "meta: commit=${meta.commitHash} device=${meta.device} " +
                    "started_at_ms=${meta.startedAtMillis}"
            )
        } ?: builder.appendLine("meta: absente")

        builder.appendLine("ticks: ${report.tickCount}")
        builder.appendLine("lignes_malformees: ${report.malformedLineCount}")

        builder.appendLine("verdicts:")
        for (verdict in SampleVerdict.entries) {
            builder.appendLine(
                "  ${verdict.name}: ${report.verdictCounts[verdict] ?: 0}"
            )
        }

        builder.appendLine("accepted_segments: ${report.acceptedSegmentCount}")
        builder.appendLine(
            "distance_totale_accumulee_m: ${formatMeters(report.totalAccumulatedMeters)}"
        )
        builder.appendLine(
            "total_final_enregistre_m: " +
                (report.finalTotalMeters?.let(::formatMeters) ?: "n/a")
        )
        builder.appendLine(
            "distance_moyenne_par_segment_m: " +
                (report.meanAcceptedSegmentMeters?.let(::formatMeters) ?: "n/a")
        )
        builder.appendLine("accuracy_m: " + renderStats(report.accuracyStats))
        builder.appendLine("vitesse_mps: " + renderStats(report.speedStats))

        builder.appendLine(
            "accepted_segments_with_speed_ge_0_5: " +
                report.acceptedSegmentsWithSpeedGe05
        )
        builder.appendLine(
            "distance_from_segments_with_speed_ge_0_5_m: " +
                formatMeters(report.distanceFromSegmentsWithSpeedGe05)
        )

        builder.appendLine("segments_acceptes (chronologie):")
        if (report.acceptedSegments.isEmpty()) {
            builder.appendLine("  (aucun)")
        }
        for (segment in report.acceptedSegments) {
            builder.appendLine(
                "  ligne=${segment.lineNumber}" +
                    " ts=${segment.sampleTimestampMillis ?: "n/a"}" +
                    " delta_m=${formatMeters(segment.deltaTotalMeters)}" +
                    " accuracy_m=${segment.accuracyMeters?.let(::formatMeters) ?: "n/a"}" +
                    " vitesse_mps=${segment.speedMetersPerSecond?.let(::formatMeters) ?: "n/a"}" +
                    " total_m=${formatMeters(segment.totalMetersAfter)}"
            )
        }

        return builder.toString()
    }

    private fun statsOf(values: List<Double>): ValueStats? {
        if (values.isEmpty()) {
            return null
        }

        return ValueStats(
            count = values.size,
            min = values.min(),
            max = values.max(),
            mean = values.sum() / values.size
        )
    }

    private fun renderStats(stats: ValueStats?): String {
        if (stats == null) {
            return "n/a"
        }

        return "min=${formatMeters(stats.min)}" +
            " max=${formatMeters(stats.max)}" +
            " moyenne=${formatMeters(stats.mean)}" +
            " (n=${stats.count})"
    }

    private fun formatMeters(value: Double): String {
        return String.format(Locale.US, "%.2f", value)
    }
}

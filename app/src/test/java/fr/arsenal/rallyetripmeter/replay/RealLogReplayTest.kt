package fr.arsenal.rallyetripmeter.replay

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/*
 * ARSENAL RALLYE — Replay des logs terrain réels (P2.a)
 *
 * Rôle :
 * - Auto-découvre les logs terrain déposés dans src/test/resources/replay/
 *   sous le nom real_*.jsonl, produit leur rapport (stdout + fichier dans
 *   build/reports/gpslog-replay/) et vérifie leurs invariants structurels.
 *
 * Usage :
 * - Récupérer le log depuis Downloads/gpslogs/ sur le téléphone, le renommer
 *   real_<scenario>_<date>.jsonl, le déposer dans les ressources, relancer.
 *   Chaque log terrain devient ainsi une régression permanente.
 *
 * Principe :
 * - Sans fichier real_*, le test passe (aucun corpus n'est encore commité).
 * - La fidélité de ré-exécution est rapportée mais non assertée ici : un log
 *   contenant des phases hors Running divergerait légitimement (le replay
 *   force la session Running) — le rapport rend la divergence visible.
 */
class RealLogReplayTest {
    @Test
    fun realFieldLogs_reportAndStructuralInvariants() {
        val replayDirectory = findReplayResourcesDirectory()
        val realLogs = replayDirectory
            ?.listFiles { file -> file.name.startsWith("real_") && file.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            .orEmpty()

        if (realLogs.isEmpty()) {
            println("Aucun log terrain real_*.jsonl : déposer les captures dans src/test/resources/replay/.")
            return
        }

        val reportDirectory = File("build/reports/gpslog-replay")
        reportDirectory.mkdirs()

        for (logFile in realLogs) {
            val parsed = TickLogReplayReader.parseFile(logFile)
            val report = TickLogReplayAnalyzer.analyze(parsed)
            val replayed = TickLogPipelineReplay.replay(parsed)
            val comparison = TickLogPipelineReplay.compare(report, replayed)

            val rendered = buildString {
                append(TickLogReplayAnalyzer.renderText(report))
                appendLine("=== FIDÉLITÉ PIPELINE (ré-exécution) ===")
                appendLine("total_enregistre_m: ${comparison.recordedTotalMeters}")
                appendLine("total_rejoue_m: ${comparison.replayedTotalMeters}")
                appendLine("totaux_identiques: ${comparison.totalsMatch}")
                appendLine("verdicts_identiques: ${comparison.verdictsMatch}")
            }

            println("##### ${logFile.name} #####")
            println(rendered)

            runCatching {
                File(reportDirectory, "${logFile.nameWithoutExtension}.txt")
                    .writeText(rendered)
            }

            // Invariant structurel : chaque tick exploitable porte un verdict.
            assertEquals(
                "${logFile.name} : somme des verdicts != nombre de ticks.",
                report.tickCount,
                report.verdictCounts.values.sum()
            )
        }
    }

    private fun findReplayResourcesDirectory(): File? {
        return CANDIDATE_DIRECTORIES
            .map(::File)
            .firstOrNull { it.isDirectory }
    }

    private companion object {
        val CANDIDATE_DIRECTORIES = listOf(
            "src/test/resources/replay",
            "app/src/test/resources/replay"
        )
    }
}

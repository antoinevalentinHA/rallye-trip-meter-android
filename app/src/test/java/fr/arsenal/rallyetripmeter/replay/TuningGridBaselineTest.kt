package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.progress.FilterTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * ARSENAL RALLYE — Tuning grid baseline + self-test (P5.a)
 *
 * Rôle :
 * - Établir la BASELINE du tuple par défaut (`FilterTuning()`) sur le corpus
 *   présent : pour chaque log, total accumulé et répartition des verdicts. Le
 *   rapport est imprimé (stdout) et écrit sous build/reports/gpslog-tuning-grid/.
 * - Auto-vérifier l'outillage de grille, SANS choisir de tuple ni changer de
 *   défaut :
 *     (1) au tuple par défaut, la grille reproduit le pipeline de fidélité
 *         existant (`TickLogPipelineReplay`) — total identique à 1e-6 près ;
 *     (2) invariant structurel par cellule : somme des verdicts = nombre de ticks ;
 *     (3) mécanique de grille : un balayage de tuples NON-CANDIDATS (étiquetés
 *         comme tels) produit bien une ligne par tuple, chacune valide
 *         structurellement. Aucune comparaison de mérite n'est faite.
 *
 * Garde-fous P5.a (impératifs) :
 * - Aucune assertion de SEUIL sur les totaux (cohérent avec RealLogReplayTest).
 * - Aucun tuple candidat retenu ; aucun défaut modifié ; FilterTuning.kt intact.
 * - Si le corpus est absent, le test passe (rien à mesurer).
 */
class TuningGridBaselineTest {

    @Test
    fun baseline_andGridMechanism_onPresentCorpus() {
        val corpus = loadCorpus()
        if (corpus.isEmpty()) {
            println("Aucun log .jsonl dans replay/ : baseline P5.a non produite (corpus absent).")
            return
        }

        // --- BASELINE : tuple par défaut sur tout le corpus ---
        val defaultTuning = FilterTuning()
        val baselineCells = corpus.map { (name, parsed) ->
            TuningGridReplay.replay(parsed, defaultTuning, tuningLabel = "default", logName = name)
        }

        baselineCells.forEach { cell ->
            // (2) invariant structurel
            assertTrue(
                "${cell.logName} : somme des verdicts (${cell.verdictCounts.values.sum()}) " +
                    "!= ticks (${cell.tickCount}).",
                cell.verdictSumMatchesTicks
            )
        }

        // (1) fidélité au pipeline existant au tuple par défaut
        corpus.forEach { (name, parsed) ->
            val viaGrid = TuningGridReplay
                .replay(parsed, defaultTuning, "default", name).totalMeters
            val viaPipeline = TickLogPipelineReplay.replay(parsed).replayedTotalMeters
            assertEquals(
                "$name : grille (défaut) != pipeline de fidélité.",
                viaPipeline,
                viaGrid,
                1.0E-6
            )
        }

        // (3) mécanique de grille sur un seul log, tuples NON-CANDIDATS
        val probeLog = corpus.first()
        val nonCandidateTunings = listOf(
            TuningGridReplay.NamedTuning("default", defaultTuning),
            // Étiquetés "non_candidat_*" : ils servent UNIQUEMENT à prouver que la
            // grille fait varier les entrées. Ils ne valent rien comme réglage et
            // ne sont pas retenus (P5.a ne choisit aucun tuple).
            TuningGridReplay.NamedTuning(
                "non_candidat_a",
                defaultTuning.copy(movementTriggerMeters = defaultTuning.movementTriggerMeters * 2.0)
            ),
            TuningGridReplay.NamedTuning(
                "non_candidat_b",
                defaultTuning.copy(detectionHysteresisSamples = defaultTuning.detectionHysteresisSamples + 4)
            ),
        )
        val gridRows = TuningGridReplay.runGrid(listOf(probeLog), nonCandidateTunings)
        assertEquals(
            "La grille doit produire une ligne par tuple sur le log sonde.",
            nonCandidateTunings.size,
            gridRows.size
        )
        gridRows.forEach { row ->
            assertTrue(
                "${row.tuningLabel}/${row.logName} : invariant structurel rompu.",
                row.verdictSumMatchesTicks
            )
        }

        // --- Rapport baseline (stdout + fichier) ---
        val table = TuningGridReplay.renderTable(baselineCells)
        println("=== Baseline P5.a — tuple par défaut (FilterTuning()) ===")
        println(table)
        writeReport(table)
    }

    private fun loadCorpus(): List<Pair<String, TickLogReplayReader.ParsedTickLog>> {
        val dir = CANDIDATE_DIRECTORIES.map(::File).firstOrNull { it.isDirectory } ?: return emptyList()
        return dir.listFiles { file -> file.name.endsWith(".jsonl") }
            ?.sortedBy { it.name }
            ?.map { it.name to TickLogReplayReader.parseFile(it) }
            .orEmpty()
    }

    private fun writeReport(table: String) {
        runCatching {
            val out = File("build/reports/gpslog-tuning-grid")
            out.mkdirs()
            File(out, "baseline.md").writeText(
                "# Baseline P5.a — tuple par défaut\n\n" +
                    "Tuple : `FilterTuning()` (constantes de production, inchangées).\n\n" +
                    table
            )
        }
    }

    private companion object {
        val CANDIDATE_DIRECTORIES = listOf(
            "src/test/resources/replay",
            "app/src/test/resources/replay"
        )
    }
}

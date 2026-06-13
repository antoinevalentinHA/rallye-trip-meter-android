package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.progress.FilterTuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/*
 * ARSENAL RALLYE — Non-régression basse vitesse (P5.c-3 étape A)
 *
 * Confirme, au tuple par défaut (movingNoiseFloorMeters = 1.4), que le plancher
 * réduit en MOVING tient les quatre régimes du corpus minimal :
 *  - arrêt M1 : 0 m sur les trois logs ;
 *  - route 6,80 km : pas de régression significative ;
 *  - urbain 8,20 km : pas d'aggravation ;
 *  - marche lente 533 m : amélioration massive (sortie nette de la zone −60 %),
 *    sans surcomptage.
 *
 * Bornes volontairement larges (non-régression, pas calage). Si un log est absent,
 * l'assertion correspondante est sautée (corpus absent -> pas d'échec), cohérent
 * avec RealLogReplayTest / TuningGridBaselineTest.
 */
class LowSpeedRegressionReplayTest {

    @Test
    fun movingNoiseFloor_holdsFourRegimes_onMinimalCorpus() {
        val dir = CANDIDATE_DIRECTORIES.map(::File).firstOrNull { it.isDirectory }
        if (dir == null) {
            println("Corpus replay absent : non-régression basse vitesse non vérifiée.")
            return
        }

        // Arrêt M1 : 0 m exact sur chacun des logs d'arrêt présents.
        for (name in STOP_LOGS) {
            total(dir, name)?.let { t ->
                assertEquals("$name : l'arrêt doit rester 0 m.", 0.0, t, 0.0)
            }
        }

        // Route référencée 6,80 km : pas de régression (bande non-régression).
        total(dir, ROUTE_LOG)?.let { t ->
            assertTrue("route = $t m hors bande non-régression.", t in 6630.0..6800.0)
        }

        // Urbain référencé 8,20 km : pas d'aggravation (bande non-régression).
        total(dir, URBAN_LOG)?.let { t ->
            assertTrue("urbain = $t m hors bande non-régression.", t in 7954.0..8200.0)
        }

        // Marche lente 533 m : amélioration massive, clairement hors zone −60 %
        // (> 0,4 × référence) et sans surcomptage (<= référence). Baseline ≈ 57,9 m.
        total(dir, WALK_LOG)?.let { t ->
            assertTrue("marche = $t m : amélioration insuffisante (zone −60 %).", t > 240.0)
            assertTrue("marche = $t m : surcomptage (> référence 533 m).", t <= 533.0)
        }
    }

    private fun total(dir: File, name: String): Double? {
        val file = File(dir, name)
        if (!file.isFile) return null
        val parsed = TickLogReplayReader.parseFile(file)
        return TuningGridReplay.replay(
            parsed = parsed,
            tuning = FilterTuning(),
            tuningLabel = "p5c3",
            logName = name
        ).totalMeters
    }

    private companion object {
        val CANDIDATE_DIRECTORIES = listOf(
            "src/test/resources/replay",
            "app/src/test/resources/replay"
        )
        val STOP_LOGS = listOf(
            "real_canape_20260611.jsonl",
            "real_canape_20260611_2351.jsonl",
            "sample_canape_synthetique.jsonl"
        )
        const val ROUTE_LOG = "real_route_voiture_20260612_odom_6_80km.jsonl"
        const val URBAN_LOG = "real_urbain_voiture_20260612_odom_8_20km_p4_2.jsonl"
        const val WALK_LOG = "real_marche_lente_20260613_ref_533m_p4_2.jsonl"
    }
}

package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogJsonl
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TickLogReplayHarnessTest {

    // ------------------------------------------------------------------
    // Log vide
    // ------------------------------------------------------------------

    @Test
    fun emptyLog_producesEmptyDeterministicReport() {
        val report = TickLogReplayAnalyzer.analyze(
            TickLogReplayReader.parseLines(emptyList())
        )

        assertEquals(0, report.tickCount)
        assertEquals(0, report.malformedLineCount)
        assertEquals(0, report.acceptedSegmentCount)
        assertEquals(0.0, report.totalAccumulatedMeters, 0.0)
        assertNull(report.meta)
        assertNull(report.finalTotalMeters)
        assertNull(report.meanAcceptedSegmentMeters)
        assertNull(report.accuracyStats)
        assertNull(report.speedStats)
        assertEquals(0, report.acceptedSegmentsWithSpeedGe05)
        assertEquals(0.0, report.distanceFromSegmentsWithSpeedGe05, 0.0)
    }

    // ------------------------------------------------------------------
    // Log sans segment accepté
    // ------------------------------------------------------------------

    @Test
    fun logWithoutAcceptedSegment_reportsZeroDistance() {
        val lines = listOf(
            TickLogJsonl.encodeMeta(sessionMeta()),
            TickLogJsonl.encodeEntry(
                entry(verdict = SampleVerdict.IGNORED_NO_ANCHOR)
            ),
            TickLogJsonl.encodeEntry(
                entry(verdict = SampleVerdict.REJECTED_STATIONARY, speed = 0.2)
            ),
            TickLogJsonl.encodeEntry(
                entry(verdict = SampleVerdict.REJECTED_NOISE, speed = null)
            )
        )

        val report = TickLogReplayAnalyzer.analyze(
            TickLogReplayReader.parseLines(lines)
        )

        assertEquals(sessionMeta(), report.meta)
        assertEquals(3, report.tickCount)
        assertEquals(0, report.acceptedSegmentCount)
        assertEquals(0.0, report.totalAccumulatedMeters, 0.0)
        assertNull(report.meanAcceptedSegmentMeters)
        assertEquals(1, report.verdictCounts[SampleVerdict.IGNORED_NO_ANCHOR])
        assertEquals(1, report.verdictCounts[SampleVerdict.REJECTED_STATIONARY])
        assertEquals(1, report.verdictCounts[SampleVerdict.REJECTED_NOISE])
        assertTrue(report.acceptedSegments.isEmpty())
    }

    // ------------------------------------------------------------------
    // Log avec segments acceptés (métrique d'hypothèse vitesse >= 0,5)
    // ------------------------------------------------------------------

    @Test
    fun logWithAcceptedSegments_splitsDistanceBySpeedHypothesis() {
        val lines = listOf(
            TickLogJsonl.encodeMeta(sessionMeta()),
            TickLogJsonl.encodeEntry(
                entry(verdict = SampleVerdict.IGNORED_NO_ANCHOR)
            ),
            TickLogJsonl.encodeEntry(
                entry(
                    verdict = SampleVerdict.ACCEPTED_SEGMENT,
                    speed = 0.8,
                    accuracy = 18.0,
                    deltaTotal = 3.0,
                    total = 3.0
                )
            ),
            TickLogJsonl.encodeEntry(
                entry(
                    verdict = SampleVerdict.ACCEPTED_SEGMENT,
                    speed = null,
                    accuracy = 4.0,
                    deltaTotal = 5.0,
                    total = 8.0
                )
            ),
            TickLogJsonl.encodeEntry(
                entry(
                    verdict = SampleVerdict.ACCEPTED_SEGMENT,
                    speed = 0.5,
                    accuracy = 22.0,
                    deltaTotal = 2.5,
                    total = 10.5
                )
            )
        )

        val report = TickLogReplayAnalyzer.analyze(
            TickLogReplayReader.parseLines(lines)
        )

        assertEquals(4, report.tickCount)
        assertEquals(3, report.acceptedSegmentCount)
        assertEquals(10.5, report.totalAccumulatedMeters, 1.0E-9)
        assertEquals(10.5, report.finalTotalMeters!!, 1.0E-9)
        assertEquals(3.5, report.meanAcceptedSegmentMeters!!, 1.0E-9)

        // Métrique d'hypothèse : 0.8 et 0.5 sont >= 0.5 ; le segment sans
        // vitesse n'y figure pas.
        assertEquals(2, report.acceptedSegmentsWithSpeedGe05)
        assertEquals(5.5, report.distanceFromSegmentsWithSpeedGe05, 1.0E-9)

        // Chronologie préservée.
        assertEquals(
            listOf(3.0, 5.0, 2.5),
            report.acceptedSegments.map { it.deltaTotalMeters }
        )
    }

    // ------------------------------------------------------------------
    // Robustesse : artefacts terrain
    // ------------------------------------------------------------------

    @Test
    fun truncatedTailLine_isCountedNotFatal() {
        val intact = TickLogJsonl.encodeEntry(
            entry(verdict = SampleVerdict.IGNORED_NO_ANCHOR)
        )
        val lines = listOf(
            TickLogJsonl.encodeMeta(sessionMeta()),
            intact,
            intact.substring(0, intact.length / 2)
        )

        val parsed = TickLogReplayReader.parseLines(lines)

        assertEquals(1, parsed.tickCount)
        assertEquals(1, parsed.malformedLines.size)
        assertEquals(3, parsed.malformedLines.single().lineNumber)
    }

    // ------------------------------------------------------------------
    // Fixture produite par le pipeline réel (scénario canapé, seed 42)
    // ------------------------------------------------------------------

    @Test
    fun syntheticCouchFixture_matchesPinnedGoldens() {
        val parsed = TickLogReplayReader.parseResource(COUCH_FIXTURE)
        val report = TickLogReplayAnalyzer.analyze(parsed)

        assertEquals(900, report.tickCount)
        assertEquals(0, report.malformedLineCount)
        assertEquals(29, report.acceptedSegmentCount)
        assertEquals(310, report.verdictCounts[SampleVerdict.REJECTED_STATIONARY])
        assertEquals(560, report.verdictCounts[SampleVerdict.REJECTED_NOISE])
        assertEquals(1, report.verdictCounts[SampleVerdict.IGNORED_NO_ANCHOR])
        assertEquals(65.25, report.totalAccumulatedMeters, 0.01)
        assertEquals(report.finalTotalMeters!!, report.totalAccumulatedMeters, 1.0E-9)

        // Hypothèse route A confirmée sur la fixture : 100 % de la distance
        // fantôme vient de segments à vitesse parasite >= 0,5 m/s.
        assertEquals(29, report.acceptedSegmentsWithSpeedGe05)
        assertEquals(
            report.totalAccumulatedMeters,
            report.distanceFromSegmentsWithSpeedGe05,
            1.0E-9
        )

        // Cohérence interne : chaque tick porte exactement un verdict.
        assertEquals(
            report.tickCount,
            report.verdictCounts.values.sum()
        )
    }

    @Test
    fun syntheticCouchFixture_replaysFaithfullyThroughRealPipeline() {
        val parsed = TickLogReplayReader.parseResource(COUCH_FIXTURE)
        val report = TickLogReplayAnalyzer.analyze(parsed)

        val replayed = TickLogPipelineReplay.replay(parsed)
        val comparison = TickLogPipelineReplay.compare(report, replayed)

        assertTrue(
            "Total rejoué (${comparison.replayedTotalMeters}) != " +
                "total enregistré (${comparison.recordedTotalMeters}).",
            comparison.totalsMatch
        )
        assertTrue(
            "Comptages de verdicts divergents : " +
                "rejoué=${comparison.replayedVerdictCounts} " +
                "enregistré=${comparison.recordedVerdictCounts}.",
            comparison.verdictsMatch
        )
    }

    @Test
    fun renderText_isDeterministic() {
        val parsed = TickLogReplayReader.parseResource(COUCH_FIXTURE)
        val report = TickLogReplayAnalyzer.analyze(parsed)

        assertEquals(
            TickLogReplayAnalyzer.renderText(report),
            TickLogReplayAnalyzer.renderText(
                TickLogReplayAnalyzer.analyze(
                    TickLogReplayReader.parseResource(COUCH_FIXTURE)
                )
            )
        )
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    private fun sessionMeta(): TickLogMeta {
        return TickLogMeta(
            commitHash = "1.0",
            device = "JVM test",
            startedAtMillis = 1_781_300_000_000L
        )
    }

    private fun entry(
        verdict: SampleVerdict,
        speed: Double? = null,
        accuracy: Double? = 10.0,
        deltaTotal: Double = 0.0,
        total: Double = 0.0
    ): TickLogEntry {
        return TickLogEntry(
            tickElapsedMillis = 1_000L,
            sampleTimestampMillis = 1_781_300_000_000L,
            sampleIsNew = true,
            latitude = 44.8378,
            longitude = -0.5792,
            accuracyMeters = accuracy,
            speedMetersPerSecond = speed,
            gpsStatus = GpsStatus.Fixed,
            sessionState = TripSessionState.Running,
            previousTimestampMillis = null,
            segmentMeters = null,
            verdict = verdict,
            floorMeters = null,
            impliedSpeedKmh = null,
            deltaTotalMeters = deltaTotal,
            totalMeters = total
        )
    }

    private companion object {
        const val COUCH_FIXTURE = "replay/sample_canape_synthetique.jsonl"
    }
}

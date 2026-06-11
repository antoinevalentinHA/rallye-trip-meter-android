package fr.arsenal.rallyetripmeter.android.diag

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogJsonl
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FileTickLogSinkTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun logMeta_writesFirstLine_readableByCodec() {
        val file = newLogFile()
        val sink = FileTickLogSink(file)

        sink.logMeta(sessionMeta())
        sink.close()

        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertEquals(sessionMeta(), TickLogJsonl.parseMeta(lines[0]))
    }

    @Test
    fun log_writesOneLinePerTick_inOrder() {
        val file = newLogFile()
        val sink = FileTickLogSink(file)

        sink.logMeta(sessionMeta())
        sink.log(tickEntry(tickElapsedMillis = 1L, totalMeters = 10.0))
        sink.log(tickEntry(tickElapsedMillis = 2L, totalMeters = 20.0))
        sink.log(tickEntry(tickElapsedMillis = 3L, totalMeters = 30.0))
        sink.close()

        val lines = file.readLines()
        assertEquals(4, lines.size)
        assertEquals(1L, TickLogJsonl.parseEntry(lines[1]).tickElapsedMillis)
        assertEquals(2L, TickLogJsonl.parseEntry(lines[2]).tickElapsedMillis)
        assertEquals(3L, TickLogJsonl.parseEntry(lines[3]).tickElapsedMillis)
    }

    @Test
    fun flush_makesPendingLinesDurable_beforeClose() {
        val file = newLogFile()
        val sink = FileTickLogSink(file)

        sink.logMeta(sessionMeta())
        sink.log(tickEntry(tickElapsedMillis = 1L, totalMeters = 10.0))
        sink.flush()

        // Lecture pendant que le writer est encore ouvert.
        val lines = file.readLines()
        assertEquals(2, lines.size)

        sink.close()
    }

    @Test
    fun close_flushesRemainingLines() {
        val file = newLogFile()
        val sink = FileTickLogSink(file)

        sink.log(tickEntry(tickElapsedMillis = 7L, totalMeters = 70.0))
        // Aucun flush explicite : le tampon (64 Ko) n'a pas atteint son seuil.
        sink.close()

        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertEquals(7L, TickLogJsonl.parseEntry(lines[0]).tickElapsedMillis)
    }

    @Test
    fun afterClose_writesAndFlushAreIgnoredWithoutError() {
        val file = newLogFile()
        val sink = FileTickLogSink(file)

        sink.log(tickEntry(tickElapsedMillis = 1L, totalMeters = 10.0))
        sink.close()
        sink.log(tickEntry(tickElapsedMillis = 2L, totalMeters = 20.0))
        sink.flush()
        sink.close()

        assertEquals(1, file.readLines().size)
    }

    @Test
    fun fullSessionFile_roundTripsThroughCodec() {
        val file = newLogFile()
        val sink = FileTickLogSink(file)
        val ticks = (1..40).map { index ->
            tickEntry(
                tickElapsedMillis = index.toLong(),
                totalMeters = index * 12.5
            )
        }

        sink.logMeta(sessionMeta())
        ticks.forEach(sink::log)
        sink.close()

        val lines = file.readLines()
        assertEquals(1 + ticks.size, lines.size)
        assertEquals(sessionMeta(), TickLogJsonl.parseMeta(lines.first()))
        val decoded = lines.drop(1).map(TickLogJsonl::parseEntry)
        assertEquals(ticks, decoded)
        assertTrue(
            "Le flush automatique (30 lignes) doit rester invisible au contenu.",
            decoded.size == 40
        )
    }

    private fun newLogFile(): File {
        return File(temporaryFolder.newFolder("gpslogs"), "gpslog_test.jsonl")
    }

    private fun sessionMeta(): TickLogMeta {
        return TickLogMeta(
            commitHash = "0.1.0",
            device = "JVM test",
            startedAtMillis = 1_781_200_000_000L
        )
    }

    private fun tickEntry(
        tickElapsedMillis: Long,
        totalMeters: Double
    ): TickLogEntry {
        return TickLogEntry(
            tickElapsedMillis = tickElapsedMillis,
            sampleTimestampMillis = 1_781_200_000_000L + tickElapsedMillis,
            sampleIsNew = true,
            latitude = 44.8378,
            longitude = -0.5792,
            accuracyMeters = 4.5,
            speedMetersPerSecond = 12.3,
            gpsStatus = GpsStatus.Fixed,
            sessionState = TripSessionState.Running,
            previousTimestampMillis = 1_781_200_000_000L,
            segmentMeters = null,
            verdict = SampleVerdict.ACCEPTED_SEGMENT,
            floorMeters = null,
            impliedSpeedKmh = null,
            deltaTotalMeters = 12.5,
            totalMeters = totalMeters
        )
    }
}

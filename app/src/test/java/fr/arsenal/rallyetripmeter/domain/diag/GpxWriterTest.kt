package fr.arsenal.rallyetripmeter.domain.diag

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * ARSENAL RALLYE — Tests GpxWriter (export a posteriori)
 *
 * Vérifie : structure GPX (trk/trkseg/trkpt), <time> présent/absent, échappement
 * XML, rejet des points invalides (null, NaN, hors bornes), trace vide -> null.
 */
class GpxWriterTest {

    @Test
    fun toGpx_withValidPoints_producesWellFormedTrack() {
        val gpx = GpxWriter.toGpx(
            listOf(
                pointAt(44.8378, -0.5792, timestamp = 1_000L),
                pointAt(44.8380, -0.5790, timestamp = 2_000L)
            )
        )

        requireNotNull(gpx)
        assertTrue(gpx.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"))
        assertTrue(gpx.contains("<gpx version=\"1.1\""))
        assertTrue(gpx.contains("<trk>"))
        assertTrue(gpx.contains("<trkseg>"))
        assertEquals(2, countOccurrences(gpx, "<trkpt "))
        assertTrue(gpx.contains("lat=\"44.8378000\""))
        assertTrue(gpx.contains("lon=\"-0.5792000\""))
        assertTrue(gpx.contains("</trkseg>"))
        assertTrue(gpx.contains("</trk>"))
        assertTrue(gpx.trimEnd().endsWith("</gpx>"))
    }

    @Test
    fun toGpx_withTimestamp_emitsIso8601UtcTime() {
        // 2021-01-01T00:00:00Z = 1_609_459_200_000 ms epoch.
        val gpx = GpxWriter.toGpx(listOf(pointAt(44.0, -0.5, timestamp = 1_609_459_200_000L)))

        requireNotNull(gpx)
        assertTrue(gpx.contains("<time>2021-01-01T00:00:00Z</time>"))
    }

    @Test
    fun toGpx_withoutTimestamp_omitsTimeElement() {
        val gpx = GpxWriter.toGpx(listOf(pointAt(44.0, -0.5, timestamp = null)))

        requireNotNull(gpx)
        assertFalse(gpx.contains("<time>"))
        assertTrue(gpx.contains("<trkpt "))
    }

    @Test
    fun toGpx_escapesXmlInTrackName() {
        val gpx = GpxWriter.toGpx(
            listOf(pointAt(44.0, -0.5, timestamp = 1_000L)),
            trackName = "Rallye <Bordeaux> & \"Médoc\""
        )

        requireNotNull(gpx)
        assertTrue(
            gpx.contains("<name>Rallye &lt;Bordeaux&gt; &amp; &quot;Médoc&quot;</name>")
        )
        // Aucun chevron brut résiduel dans le nom.
        assertFalse(gpx.contains("<name>Rallye <Bordeaux>"))
    }

    @Test
    fun toGpx_skipsPointsWithNullCoordinates() {
        val gpx = GpxWriter.toGpx(
            listOf(
                pointAt(44.0, -0.5, timestamp = 1_000L),
                nominal().copy(latitude = null, longitude = null),
                nominal().copy(latitude = 44.1, longitude = null)
            )
        )

        requireNotNull(gpx)
        assertEquals(1, countOccurrences(gpx, "<trkpt "))
    }

    @Test
    fun toGpx_skipsNanAndOutOfRangePoints() {
        val gpx = GpxWriter.toGpx(
            listOf(
                pointAt(44.0, -0.5, timestamp = 1_000L),
                pointAt(Double.NaN, -0.5, timestamp = 2_000L),
                pointAt(91.0, -0.5, timestamp = 3_000L),
                pointAt(44.0, 200.0, timestamp = 4_000L),
                pointAt(Double.POSITIVE_INFINITY, -0.5, timestamp = 5_000L)
            )
        )

        requireNotNull(gpx)
        assertEquals(1, countOccurrences(gpx, "<trkpt "))
    }

    @Test
    fun toGpx_withNoTrackablePoints_returnsNull() {
        val gpx = GpxWriter.toGpx(
            listOf(
                nominal().copy(latitude = null, longitude = null),
                pointAt(Double.NaN, Double.NaN, timestamp = 1_000L)
            )
        )

        assertNull(gpx)
    }

    @Test
    fun toGpx_withEmptyList_returnsNull() {
        assertNull(GpxWriter.toGpx(emptyList()))
    }

    @Test
    fun hasTrackablePoints_reflectsPresenceOfValidPoint() {
        assertTrue(GpxWriter.hasTrackablePoints(listOf(pointAt(44.0, -0.5, timestamp = 1L))))
        assertFalse(
            GpxWriter.hasTrackablePoints(
                listOf(nominal().copy(latitude = null, longitude = null))
            )
        )
        assertFalse(GpxWriter.hasTrackablePoints(emptyList()))
    }

    @Test
    fun toGpx_formatsCoordinatesWithDotDecimalSeparator() {
        val gpx = GpxWriter.toGpx(listOf(pointAt(44.123456, -0.654321, timestamp = 1L)))

        requireNotNull(gpx)
        assertFalse("Séparateur virgule interdit en GPX", gpx.contains("lat=\"44,"))
        assertTrue(gpx.contains("lat=\"44.1234560\""))
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    private fun countOccurrences(haystack: String, needle: String): Int {
        var count = 0
        var index = haystack.indexOf(needle)
        while (index >= 0) {
            count += 1
            index = haystack.indexOf(needle, index + needle.length)
        }
        return count
    }

    private fun pointAt(
        latitude: Double,
        longitude: Double,
        timestamp: Long?
    ): TickLogEntry {
        return nominal().copy(
            latitude = latitude,
            longitude = longitude,
            sampleTimestampMillis = timestamp
        )
    }

    private fun nominal(): TickLogEntry {
        return TickLogEntry(
            tickElapsedMillis = 0L,
            sampleTimestampMillis = 0L,
            sampleIsNew = true,
            latitude = 44.0,
            longitude = -0.5,
            accuracyMeters = 5.0,
            speedMetersPerSecond = 1.0,
            gpsStatus = GpsStatus.Fixed,
            sessionState = TripSessionState.Running,
            previousTimestampMillis = null,
            segmentMeters = null,
            verdict = SampleVerdict.ACCEPTED_SEGMENT,
            floorMeters = null,
            impliedSpeedKmh = null,
            deltaTotalMeters = 0.0,
            totalMeters = 0.0
        )
    }
}

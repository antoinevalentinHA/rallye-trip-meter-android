package fr.arsenal.rallyetripmeter.domain.diag

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * ARSENAL RALLYE — Tests GpxTrackReader (relecture a posteriori)
 *
 * Vérifie le parse d'un GPX (notamment celui produit par GpxWriter) : points
 * multiples, time présent/absent, coordonnées invalides ignorées, trace vide.
 */
class GpxTrackReaderTest {

    @Test
    fun parse_gpxProducedByWriter_roundTripsPoints() {
        val entries = listOf(
            entry(44.8378, -0.5792, timestamp = 1_609_459_200_000L),
            entry(44.8380, -0.5790, timestamp = 1_609_459_201_000L),
            entry(44.8382, -0.5788, timestamp = 1_609_459_202_000L)
        )
        val gpx = requireNotNull(GpxWriter.toGpx(entries, trackName = "Rallye Test"))

        val track = GpxTrackReader.parse(gpx)

        assertEquals(3, track.pointCount)
        assertEquals(44.8378, track.points[0].latitude, 1e-7)
        assertEquals(-0.5792, track.points[0].longitude, 1e-7)
        assertEquals("Rallye Test", track.name)
    }

    @Test
    fun parse_extractsTimeWhenPresent() {
        val gpx = requireNotNull(
            GpxWriter.toGpx(listOf(entry(44.0, -0.5, timestamp = 1_609_459_200_000L)))
        )

        val track = GpxTrackReader.parse(gpx)

        assertEquals(1, track.pointCount)
        assertEquals(1_609_459_200_000L, track.points[0].timeMillis)
    }

    @Test
    fun parse_pointWithoutTime_hasNullTime() {
        val gpx = requireNotNull(
            GpxWriter.toGpx(listOf(entry(44.0, -0.5, timestamp = null)))
        )

        val track = GpxTrackReader.parse(gpx)

        assertEquals(1, track.pointCount)
        assertNull(track.points[0].timeMillis)
    }

    @Test
    fun parse_computesDurationFromFirstAndLastTime() {
        val gpx = requireNotNull(
            GpxWriter.toGpx(
                listOf(
                    entry(44.0, -0.5, timestamp = 1_000_000L),
                    entry(44.1, -0.4, timestamp = 1_060_000L)
                )
            )
        )

        val track = GpxTrackReader.parse(gpx)

        assertEquals(60_000L, track.durationMillis)
        assertEquals(1_000_000L, track.startTimeMillis)
        assertEquals(1_060_000L, track.endTimeMillis)
    }

    @Test
    fun parse_ignoresPointsWithInvalidCoordinates() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" creator="rallye-trip-meter-android" xmlns="http://www.topografix.com/GPX/1/1">
              <trk>
                <name>Mixed</name>
                <trkseg>
                  <trkpt lat="44.0" lon="-0.5"></trkpt>
                  <trkpt lat="999.0" lon="-0.5"></trkpt>
                  <trkpt lat="44.0" lon="500.0"></trkpt>
                  <trkpt lat="abc" lon="-0.5"></trkpt>
                  <trkpt lon="-0.5"></trkpt>
                  <trkpt lat="44.1" lon="-0.4"></trkpt>
                </trkseg>
              </trk>
            </gpx>
        """.trimIndent()

        val track = GpxTrackReader.parse(gpx)

        assertEquals(2, track.pointCount)
        assertEquals(44.0, track.points[0].latitude, 1e-7)
        assertEquals(44.1, track.points[1].latitude, 1e-7)
    }

    @Test
    fun parse_emptyString_returnsEmptyTrack() {
        val track = GpxTrackReader.parse("")

        assertEquals(0, track.pointCount)
        assertNull(track.startTimeMillis)
        assertNull(track.durationMillis)
    }

    @Test
    fun parse_gpxWithoutTrackpoints_returnsEmptyTrack() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Empty</name><trkseg></trkseg></trk>
            </gpx>
        """.trimIndent()

        val track = GpxTrackReader.parse(gpx)

        assertEquals(0, track.pointCount)
        assertEquals("Empty", track.name)
    }

    @Test
    fun parse_unescapesTrackName() {
        val gpx = requireNotNull(
            GpxWriter.toGpx(
                listOf(entry(44.0, -0.5, timestamp = 1L)),
                trackName = "A <b> & \"c\""
            )
        )

        val track = GpxTrackReader.parse(gpx)

        assertEquals("A <b> & \"c\"", track.name)
    }

    @Test
    fun parse_durationNull_whenNoTimes() {
        val gpx = requireNotNull(
            GpxWriter.toGpx(
                listOf(
                    entry(44.0, -0.5, timestamp = null),
                    entry(44.1, -0.4, timestamp = null)
                )
            )
        )

        val track = GpxTrackReader.parse(gpx)

        assertEquals(2, track.pointCount)
        assertNull(track.durationMillis)
    }

    // ------------------------------------------------------------------

    private fun entry(
        latitude: Double,
        longitude: Double,
        timestamp: Long?
    ): TickLogEntry {
        return TickLogEntry(
            tickElapsedMillis = 0L,
            sampleTimestampMillis = timestamp,
            sampleIsNew = true,
            latitude = latitude,
            longitude = longitude,
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

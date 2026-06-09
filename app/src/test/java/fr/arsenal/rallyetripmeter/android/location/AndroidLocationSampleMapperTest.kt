package fr.arsenal.rallyetripmeter.android.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidLocationSampleMapperTest {
    @Test
    fun buildLocationSample_mapsAllFields() {
        val sample = buildLocationSample(
            latitude = 44.8378,
            longitude = -0.5792,
            altitudeMeters = 12.0,
            timestampMillis = 1_000L,
            accuracyMeters = 4.0,
            speedMetersPerSecond = 12.0
        )

        assertEquals(44.8378, sample.point.latitude, 0.0)
        assertEquals(-0.5792, sample.point.longitude, 0.0)
        assertEquals(12.0, sample.point.altitudeMeters!!, 0.0)
        assertEquals(1_000L, sample.point.timestampMillis!!)
        assertEquals(4.0, sample.accuracyMeters!!, 0.0)
        assertEquals(12.0, sample.speedMetersPerSecond!!, 0.0)
    }

    @Test
    fun buildLocationSample_preservesNullOptionals() {
        val sample = buildLocationSample(
            latitude = 0.0,
            longitude = 0.0,
            altitudeMeters = null,
            timestampMillis = null,
            accuracyMeters = null,
            speedMetersPerSecond = null
        )

        assertNull(sample.point.altitudeMeters)
        assertNull(sample.point.timestampMillis)
        assertNull(sample.accuracyMeters)
        assertNull(sample.speedMetersPerSecond)
    }
}

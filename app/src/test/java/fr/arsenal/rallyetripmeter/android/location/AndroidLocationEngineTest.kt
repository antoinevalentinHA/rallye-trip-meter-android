package fr.arsenal.rallyetripmeter.android.location

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidLocationEngineTest {
    @Test
    fun getGpsStatus_returnsUnavailable() {
        val engine = AndroidLocationEngine()

        val result = engine.getGpsStatus()

        assertEquals(GpsStatus.Unavailable, result)
    }

    @Test
    fun getLastLocationSample_returnsNull() {
        val engine = AndroidLocationEngine()

        val result = engine.getLastLocationSample()

        assertNull(result)
    }
}

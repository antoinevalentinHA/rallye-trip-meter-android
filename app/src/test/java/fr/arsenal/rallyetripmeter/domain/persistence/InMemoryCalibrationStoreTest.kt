package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient
import org.junit.Assert.assertEquals
import org.junit.Test

class InMemoryCalibrationStoreTest {

    @Test
    fun load_defaultsToNeutralWhenNothingSaved() {
        val store = InMemoryCalibrationStore()
        assertEquals(1000, store.load().perMille)
    }

    @Test
    fun save_thenLoad_roundTrips() {
        val store = InMemoryCalibrationStore()
        store.save(CalibrationCoefficient.of(1020))
        assertEquals(1020, store.load().perMille)
        assertEquals("1.020", store.load().format())
    }

    @Test
    fun save_clampsThroughCoefficient() {
        val store = InMemoryCalibrationStore()
        store.save(CalibrationCoefficient.of(1200))
        assertEquals(1100, store.load().perMille)
    }
}

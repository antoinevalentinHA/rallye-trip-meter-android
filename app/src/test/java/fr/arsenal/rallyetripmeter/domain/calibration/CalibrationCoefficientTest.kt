package fr.arsenal.rallyetripmeter.domain.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalibrationCoefficientTest {

    @Test
    fun default_isNeutral() {
        val c = CalibrationCoefficient.default()
        assertEquals(1000, c.perMille)
        assertEquals(1.0, c.toDouble(), 1e-9)
        assertEquals("1.000", c.format())
        assertFalse(c.isActive())
    }

    @Test
    fun of_clampsBelowMin() {
        assertEquals(900, CalibrationCoefficient.of(800).perMille)
    }

    @Test
    fun of_clampsAboveMax() {
        assertEquals(1100, CalibrationCoefficient.of(1200).perMille)
    }

    @Test
    fun of_keepsValueWithinBounds() {
        assertEquals(1020, CalibrationCoefficient.of(1020).perMille)
    }

    @Test
    fun adjust_smallStepsUpAndDown() {
        val base = CalibrationCoefficient.default()
        assertEquals(1001, base.adjustedBy(CalibrationCoefficient.STEP_SMALL).perMille)
        assertEquals(999, base.adjustedBy(-CalibrationCoefficient.STEP_SMALL).perMille)
    }

    @Test
    fun adjust_largeStepsUpAndDown() {
        val base = CalibrationCoefficient.default()
        assertEquals(1010, base.adjustedBy(CalibrationCoefficient.STEP_LARGE).perMille)
        assertEquals(990, base.adjustedBy(-CalibrationCoefficient.STEP_LARGE).perMille)
    }

    @Test
    fun adjust_clampsAtBounds() {
        assertEquals(1100, CalibrationCoefficient.of(1100).adjustedBy(CalibrationCoefficient.STEP_LARGE).perMille)
        assertEquals(900, CalibrationCoefficient.of(900).adjustedBy(-CalibrationCoefficient.STEP_LARGE).perMille)
    }

    @Test
    fun reset_returnsNeutral() {
        assertEquals(1000, CalibrationCoefficient.of(1050).reset().perMille)
    }

    @Test
    fun format_examples() {
        assertEquals("1.000", CalibrationCoefficient.of(1000).format())
        assertEquals("1.020", CalibrationCoefficient.of(1020).format())
        assertEquals("0.995", CalibrationCoefficient.of(995).format())
        assertEquals("1.100", CalibrationCoefficient.of(1100).format())
        assertEquals("0.900", CalibrationCoefficient.of(900).format())
    }

    @Test
    fun adjust_noFloatDriftOverManySteps() {
        var c = CalibrationCoefficient.default()
        repeat(20) { c = c.adjustedBy(CalibrationCoefficient.STEP_SMALL) }
        assertEquals(1020, c.perMille)
        assertEquals("1.020", c.format())
        assertEquals(1.02, c.toDouble(), 1e-9)
        assertTrue(c.isActive())
    }
}

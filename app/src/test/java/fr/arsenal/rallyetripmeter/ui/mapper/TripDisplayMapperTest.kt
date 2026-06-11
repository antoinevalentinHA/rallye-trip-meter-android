package fr.arsenal.rallyetripmeter.ui.mapper

import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.ui.model.UiGpsStatus
import fr.arsenal.rallyetripmeter.ui.model.UiSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripDisplayMapperTest {
    @Test
    fun toTripDisplayState_withZeroDistances_formatsAsZeroKilometers() {
        val state = TripState(
            totalDistanceMeters = 0.0,
            partialDistanceMeters = 0.0
        )

        val display = state.toTripDisplayState()

        assertEquals("0.00 km", display.totalDistanceText)
        assertEquals("0.00 km", display.partialDistanceText)
    }

    @Test
    fun toTripDisplayState_withRepresentativeDistance_roundsToTwoDecimals() {
        val state = TripState(
            partialDistanceMeters = 1234.0
        )

        val display = state.toTripDisplayState()

        assertEquals("1.23 km", display.partialDistanceText)
    }

    @Test
    fun toTripDisplayState_mapsTotalAndPartialIndependently() {
        val state = TripState(
            totalDistanceMeters = 124370.0,
            partialDistanceMeters = 800.0
        )

        val display = state.toTripDisplayState()

        assertEquals("124.37 km", display.totalDistanceText)
        assertEquals("0.80 km", display.partialDistanceText)
    }

    @Test
    fun toTripDisplayState_whenGpsUnavailable_mapsToUnknown() {
        val display = TripState(gpsStatus = GpsStatus.Unavailable).toTripDisplayState()

        assertEquals(UiGpsStatus.Unknown, display.gpsStatus)
    }

    @Test
    fun toTripDisplayState_whenGpsSearching_mapsToSearching() {
        val display = TripState(gpsStatus = GpsStatus.Searching).toTripDisplayState()

        assertEquals(UiGpsStatus.Searching, display.gpsStatus)
    }

    @Test
    fun toTripDisplayState_whenGpsFixed_mapsToOk() {
        val display = TripState(gpsStatus = GpsStatus.Fixed).toTripDisplayState()

        assertEquals(UiGpsStatus.Ok, display.gpsStatus)
    }

    @Test
    fun toTripDisplayState_whenSessionStopped_mapsToStopped() {
        val display = TripState(sessionState = TripSessionState.Stopped).toTripDisplayState()

        assertEquals(UiSessionStatus.Stopped, display.sessionStatus)
    }

    @Test
    fun toTripDisplayState_whenSessionRunning_mapsToActive() {
        val display = TripState(sessionState = TripSessionState.Running).toTripDisplayState()

        assertEquals(UiSessionStatus.Active, display.sessionStatus)
    }

    @Test
    fun toTripDisplayState_whenSessionPaused_mapsToPaused() {
        val display = TripState(sessionState = TripSessionState.Paused).toTripDisplayState()

        assertEquals(UiSessionStatus.Paused, display.sessionStatus)
    }

    @Test
    fun toTripDisplayState_whenSpeedAbsent_returnsDashText() {
        val display = TripState().toTripDisplayState()

        assertEquals("—", display.speedText)
    }

    @Test
    fun toTripDisplayState_whenSpeedPresent_formatsAsRoundedKmh() {
        val display = TripState(speedMetersPerSecond = 21.0).toTripDisplayState()

        assertEquals("76 km/h", display.speedText)
    }

    @Test
    fun toTripDisplayState_whenAccuracyAbsent_returnsNullAccuracyText() {
        val display = TripState().toTripDisplayState()

        assertNull(display.gpsAccuracyText)
    }

    @Test
    fun toTripDisplayState_whenAccuracyPresent_formatsWithSign() {
        val display = TripState(accuracyMeters = 4.0).toTripDisplayState()

        assertEquals("±4 m", display.gpsAccuracyText)
    }

    @Test
    fun toTripDisplayState_whenAccuracyPresent_roundsToWholeMeters() {
        val display = TripState(accuracyMeters = 3.6).toTripDisplayState()

        assertEquals("±4 m", display.gpsAccuracyText)
    }

    @Test
    fun toTripDisplayState_whenAccuracyPresent_gpsStatusTextExcludesAccuracy() {
        val display = TripState(
            gpsStatus = GpsStatus.Fixed,
            accuracyMeters = 209.0
        ).toTripDisplayState()

        assertEquals(UiGpsStatus.Ok.label, display.gpsStatusText)
        assertEquals("±209 m", display.gpsAccuracyText)
    }

    @Test
    fun toTripDisplayState_withDefaultCalibration_leavesDistancesUnchanged() {
        val state = TripState(totalDistanceMeters = 8000.0, partialDistanceMeters = 5000.0)
        val display = state.toTripDisplayState(CalibrationCoefficient.default())
        assertEquals("8.00 km", display.totalDistanceText)
        assertEquals("5.00 km", display.partialDistanceText)
    }

    @Test
    fun toTripDisplayState_withCalibration_scalesPartialAndTotal() {
        val state = TripState(totalDistanceMeters = 8000.0, partialDistanceMeters = 8000.0)
        val display = state.toTripDisplayState(CalibrationCoefficient.of(1020))
        assertEquals("8.16 km", display.partialDistanceText)
        assertEquals("8.16 km", display.totalDistanceText)
    }

    @Test
    fun toTripDisplayState_withCalibration_doesNotAffectSpeedOrGps() {
        val state = TripState(
            totalDistanceMeters = 8000.0,
            partialDistanceMeters = 8000.0,
            speedMetersPerSecond = 21.0,
            accuracyMeters = 4.0,
            gpsStatus = GpsStatus.Fixed
        )
        val calibrated = state.toTripDisplayState(CalibrationCoefficient.of(1020))
        val neutral = state.toTripDisplayState()
        assertEquals(neutral.speedText, calibrated.speedText)
        assertEquals(neutral.gpsAccuracyText, calibrated.gpsAccuracyText)
        assertEquals(neutral.gpsStatusText, calibrated.gpsStatusText)
    }
}

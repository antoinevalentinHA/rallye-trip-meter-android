package fr.arsenal.rallyetripmeter.ui.mapper

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
    fun toTripDisplayState_speedText_isCurrentPlaceholder() {
        val display = TripState().toTripDisplayState()

        assertEquals("0 km/h", display.speedText)
    }

    @Test
    fun toTripDisplayState_gpsAccuracyText_isNullPlaceholder() {
        val display = TripState().toTripDisplayState()

        assertNull(display.gpsAccuracyText)
    }
}

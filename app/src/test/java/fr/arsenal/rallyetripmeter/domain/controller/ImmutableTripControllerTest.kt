package fr.arsenal.rallyetripmeter.domain.controller

import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

class ImmutableTripControllerTest {
    private val controller = ImmutableTripController()

    @Test
    fun start_setsSessionStateToRunning() {
        val initialState = TripState(
            sessionState = TripSessionState.Stopped
        )

        val result = controller.start(initialState)

        assertEquals(TripSessionState.Running, result.sessionState)
    }

    @Test
    fun pause_fromRunning_setsSessionStateToPaused() {
        val initialState = TripState(
            sessionState = TripSessionState.Running
        )

        val result = controller.pause(initialState)

        assertEquals(TripSessionState.Paused, result.sessionState)
    }

    @Test
    fun pause_fromStopped_keepsStateUnchanged() {
        val initialState = TripState(
            sessionState = TripSessionState.Stopped
        )

        val result = controller.pause(initialState)

        assertEquals(initialState, result)
    }

    @Test
    fun resume_fromPaused_setsSessionStateToRunning() {
        val initialState = TripState(
            sessionState = TripSessionState.Paused
        )

        val result = controller.resume(initialState)

        assertEquals(TripSessionState.Running, result.sessionState)
    }

    @Test
    fun resume_fromStopped_keepsStateUnchanged() {
        val initialState = TripState(
            sessionState = TripSessionState.Stopped
        )

        val result = controller.resume(initialState)

        assertEquals(initialState, result)
    }

    @Test
    fun stop_setsSessionStateToStopped() {
        val initialState = TripState(
            sessionState = TripSessionState.Running
        )

        val result = controller.stop(initialState)

        assertEquals(TripSessionState.Stopped, result.sessionState)
    }

    @Test
    fun resetPartial_setsPartialDistanceToZero() {
        val initialState = TripState(
            partialDistanceMeters = 1234.0
        )

        val result = controller.resetPartial(initialState)

        assertEquals(0.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun adjustPartial_withPositiveDelta_increasesPartialDistance() {
        val initialState = TripState(
            partialDistanceMeters = 100.0
        )

        val result = controller.adjustPartial(
            state = initialState,
            deltaMeters = 50.0
        )

        assertEquals(150.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun adjustPartial_withNegativeDelta_decreasesPartialDistance() {
        val initialState = TripState(
            partialDistanceMeters = 100.0
        )

        val result = controller.adjustPartial(
            state = initialState,
            deltaMeters = -40.0
        )

        assertEquals(60.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun adjustPartial_belowZero_clampsPartialDistanceToZero() {
        val initialState = TripState(
            partialDistanceMeters = 30.0
        )

        val result = controller.adjustPartial(
            state = initialState,
            deltaMeters = -100.0
        )

        assertEquals(0.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun resetTotal_setsTotalDistanceToZero() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            partialDistanceMeters = 56.0,
            sessionState = TripSessionState.Running
        )

        val result = controller.resetTotal(initialState)

        assertEquals(0.0, result.totalDistanceMeters, 0.0)
    }

    @Test
    fun resetTotal_keepsPartialDistance() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            partialDistanceMeters = 56.0
        )

        val result = controller.resetTotal(initialState)

        assertEquals(56.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun resetTotal_keepsSessionState() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            sessionState = TripSessionState.Running
        )

        val result = controller.resetTotal(initialState)

        assertEquals(TripSessionState.Running, result.sessionState)
    }

    @Test
    fun resetTrip_setsTotalDistanceToZero() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            partialDistanceMeters = 56.0
        )

        val result = controller.resetTrip(initialState)

        assertEquals(0.0, result.totalDistanceMeters, 0.0)
    }

    @Test
    fun resetTrip_setsPartialDistanceToZero() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            partialDistanceMeters = 56.0
        )

        val result = controller.resetTrip(initialState)

        assertEquals(0.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun resetTrip_keepsStoppedSession() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            sessionState = TripSessionState.Stopped
        )

        val result = controller.resetTrip(initialState)

        assertEquals(TripSessionState.Stopped, result.sessionState)
    }

    @Test
    fun resetTrip_keepsRunningSession() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            sessionState = TripSessionState.Running
        )

        val result = controller.resetTrip(initialState)

        assertEquals(TripSessionState.Running, result.sessionState)
    }

    @Test
    fun resetTrip_keepsPausedSession() {
        val initialState = TripState(
            totalDistanceMeters = 1234.0,
            sessionState = TripSessionState.Paused
        )

        val result = controller.resetTrip(initialState)

        assertEquals(TripSessionState.Paused, result.sessionState)
    }
}

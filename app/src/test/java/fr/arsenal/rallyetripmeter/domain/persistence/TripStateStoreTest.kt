package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripStateStoreTest {
    @Test
    fun toTripStateSnapshot_capturesDurableFields() {
        val state = TripState(
            sessionState = TripSessionState.Running,
            totalDistanceMeters = 1234.0,
            partialDistanceMeters = 56.0
        )

        val snapshot = state.toTripStateSnapshot()

        assertEquals(1234.0, snapshot.totalDistanceMeters, 0.0)
        assertEquals(56.0, snapshot.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, snapshot.sessionState)
    }

    @Test
    fun toTripStateSnapshot_ignoresEphemeralGpsFields() {
        val state = TripState(
            sessionState = TripSessionState.Paused,
            totalDistanceMeters = 10.0,
            partialDistanceMeters = 5.0,
            gpsStatus = GpsStatus.Fixed,
            accuracyMeters = 4.0,
            speedMetersPerSecond = 21.0
        )

        val restored = state.toTripStateSnapshot().toTripState()

        assertEquals(GpsStatus.Unavailable, restored.gpsStatus)
        assertNull(restored.accuracyMeters)
        assertNull(restored.speedMetersPerSecond)
    }

    @Test
    fun toTripState_restoresDurableFields() {
        val snapshot = TripStateSnapshot(
            totalDistanceMeters = 800.0,
            partialDistanceMeters = 120.0,
            sessionState = TripSessionState.Running
        )

        val state = snapshot.toTripState()

        assertEquals(800.0, state.totalDistanceMeters, 0.0)
        assertEquals(120.0, state.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, state.sessionState)
    }

    @Test
    fun noOpStore_loadReturnsNullAfterSave() {
        val store: TripStateStore = NoOpTripStateStore()

        store.save(
            TripStateSnapshot(
                totalDistanceMeters = 999.0,
                partialDistanceMeters = 99.0,
                sessionState = TripSessionState.Running
            )
        )

        assertNull(store.load())
    }
}

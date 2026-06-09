package fr.arsenal.rallyetripmeter.domain.progress

import fr.arsenal.rallyetripmeter.domain.distance.DistanceEngine
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import org.junit.Assert.assertEquals
import org.junit.Test

class DistanceTripProgressEngineTest {
    private val distanceEngine = FakeDistanceEngine(
        distanceMeters = 12.5
    )

    private val engine = DistanceTripProgressEngine(
        distanceEngine = distanceEngine
    )

    @Test
    fun applyLocationSample_withoutPreviousSample_keepsStateUnchanged() {
        val state = TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Running
        )

        val result = engine.applyLocationSample(
            state = state,
            previousSample = null,
            currentSample = currentSample()
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenStopped_keepsStateUnchanged() {
        val state = TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Stopped
        )

        val result = engine.applyLocationSample(
            state = state,
            previousSample = previousSample(),
            currentSample = currentSample()
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenPaused_keepsStateUnchanged() {
        val state = TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Paused
        )

        val result = engine.applyLocationSample(
            state = state,
            previousSample = previousSample(),
            currentSample = currentSample()
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenRunning_addsDistanceToTotalAndPartial() {
        val state = TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Running
        )

        val result = engine.applyLocationSample(
            state = state,
            previousSample = previousSample(),
            currentSample = currentSample()
        )

        assertEquals(112.5, result.totalDistanceMeters, 0.0)
        assertEquals(32.5, result.partialDistanceMeters, 0.0)
    }

    private fun previousSample(): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792
            )
        )
    }

    private fun currentSample(): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8379,
                longitude = -0.5793
            )
        )
    }

    private class FakeDistanceEngine(
        private val distanceMeters: Double
    ) : DistanceEngine {
        override fun computeDistanceMeters(
            previous: GeoPoint,
            current: GeoPoint
        ): Double {
            return distanceMeters
        }
    }
}

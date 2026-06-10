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

    @Test
    fun applyLocationSample_whenDistanceBelowNoiseFloor_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(1.5)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L),
            currentSample = sampleAt(1_000L)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenTimeDeltaNotPositive_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(20.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(1_000L),
            currentSample = sampleAt(1_000L)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenImplicitSpeedTooHigh_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(100.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L),
            currentSample = sampleAt(1_000L)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenPlausibleMovement_addsDistance() {
        val state = runningState()
        val filteringEngine = engineWithDistance(12.5)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L),
            currentSample = sampleAt(1_000L)
        )

        assertEquals(112.5, result.totalDistanceMeters, 0.0)
        assertEquals(32.5, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_whenTimestampsMissing_skipsSpeedCheck() {
        val state = runningState()
        val filteringEngine = engineWithDistance(500.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = previousSample(),
            currentSample = currentSample()
        )

        assertEquals(600.0, result.totalDistanceMeters, 0.0)
        assertEquals(520.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_appliesCalibrationFactorToAccumulatedDistance() {
        val state = runningState()
        val calibratedEngine = DistanceTripProgressEngine(
            distanceEngine = FakeDistanceEngine(distanceMeters = 20.0),
            calibrationFactor = 1.5
        )

        val result = calibratedEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L),
            currentSample = sampleAt(1_000L)
        )

        assertEquals(130.0, result.totalDistanceMeters, 0.0)
        assertEquals(50.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_whenSegmentBelowAccuracyFloor_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(8.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, accuracyMeters = 10.0),
            currentSample = sampleAt(1_000L, accuracyMeters = 10.0)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenAccuracyTooPoor_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(12.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, accuracyMeters = 30.0),
            currentSample = sampleAt(1_000L, accuracyMeters = 30.0)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenReportedSpeedNearZero_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(12.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, speedMetersPerSecond = 0.2),
            currentSample = sampleAt(1_000L, speedMetersPerSecond = 0.2)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenRealMovementWithGoodAccuracy_addsDistance() {
        val state = runningState()
        val filteringEngine = engineWithDistance(12.5)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, accuracyMeters = 5.0, speedMetersPerSecond = 8.0),
            currentSample = sampleAt(1_000L, accuracyMeters = 5.0, speedMetersPerSecond = 8.0)
        )

        assertEquals(112.5, result.totalDistanceMeters, 0.0)
        assertEquals(32.5, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_whenMovingBelowAccuracyFloor_addsDistance() {
        val state = runningState()
        val filteringEngine = engineWithDistance(8.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, accuracyMeters = 12.0, speedMetersPerSecond = 8.0),
            currentSample = sampleAt(1_000L, accuracyMeters = 12.0, speedMetersPerSecond = 8.0)
        )

        assertEquals(108.0, result.totalDistanceMeters, 0.0)
        assertEquals(28.0, result.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_whenDriftBelowAccuracyFloorWithoutSpeed_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(8.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, accuracyMeters = 12.0),
            currentSample = sampleAt(1_000L, accuracyMeters = 12.0)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenStationarySpeedDespiteSpeedPresent_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(8.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, speedMetersPerSecond = 0.3),
            currentSample = sampleAt(1_000L, speedMetersPerSecond = 0.3)
        )

        assertEquals(state, result)
    }

    @Test
    fun applyLocationSample_whenImplausibleJumpWhileMoving_keepsStateUnchanged() {
        val state = runningState()
        val filteringEngine = engineWithDistance(300.0)

        val result = filteringEngine.applyLocationSample(
            state = state,
            previousSample = sampleAt(0L, speedMetersPerSecond = 8.0),
            currentSample = sampleAt(1_000L, speedMetersPerSecond = 8.0)
        )

        assertEquals(state, result)
    }

    private fun runningState(): TripState {
        return TripState(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 20.0,
            sessionState = TripSessionState.Running
        )
    }

    private fun engineWithDistance(distanceMeters: Double): DistanceTripProgressEngine {
        return DistanceTripProgressEngine(
            distanceEngine = FakeDistanceEngine(distanceMeters = distanceMeters)
        )
    }

    private fun sampleAt(
        timestampMillis: Long,
        accuracyMeters: Double? = null,
        speedMetersPerSecond: Double? = null
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = 44.8378,
                longitude = -0.5792,
                timestampMillis = timestampMillis
            ),
            accuracyMeters = accuracyMeters,
            speedMetersPerSecond = speedMetersPerSecond
        )
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

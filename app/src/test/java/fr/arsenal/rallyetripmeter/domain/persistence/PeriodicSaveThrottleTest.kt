package fr.arsenal.rallyetripmeter.domain.persistence

import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PeriodicSaveThrottleTest {
    private var now = 0L

    private fun throttle(intervalMillis: Long = 15_000L): PeriodicSaveThrottle {
        return PeriodicSaveThrottle(
            intervalMillis = intervalMillis,
            nowMillis = { now }
        )
    }

    private fun runningSnapshot(
        totalDistanceMeters: Double = 100.0,
        partialDistanceMeters: Double = 10.0
    ): TripStateSnapshot {
        return TripStateSnapshot(
            totalDistanceMeters = totalDistanceMeters,
            partialDistanceMeters = partialDistanceMeters,
            sessionState = TripSessionState.Running
        )
    }

    @Test
    fun pollSnapshotToSave_firstRunningPoll_returnsSnapshot() {
        now = 0L
        val sut = throttle()

        val result = sut.pollSnapshotToSave(runningSnapshot())

        assertEquals(runningSnapshot(), result)
    }

    @Test
    fun pollSnapshotToSave_rapidPolls_savesOnlyOnce() {
        now = 0L
        val sut = throttle(intervalMillis = 15_000L)

        val first = sut.pollSnapshotToSave(runningSnapshot(totalDistanceMeters = 100.0))
        now = 1_000L
        val second = sut.pollSnapshotToSave(runningSnapshot(totalDistanceMeters = 130.0))

        assertEquals(runningSnapshot(totalDistanceMeters = 100.0), first)
        assertNull(second)
    }

    @Test
    fun pollSnapshotToSave_afterInterval_savesAgainWhenChanged() {
        now = 0L
        val sut = throttle(intervalMillis = 15_000L)

        sut.pollSnapshotToSave(runningSnapshot(totalDistanceMeters = 100.0))
        now = 16_000L
        val result = sut.pollSnapshotToSave(runningSnapshot(totalDistanceMeters = 200.0))

        assertEquals(runningSnapshot(totalDistanceMeters = 200.0), result)
    }

    @Test
    fun pollSnapshotToSave_whenNotRunning_returnsNull() {
        now = 0L
        val sut = throttle()

        val stopped = TripStateSnapshot(
            totalDistanceMeters = 100.0,
            partialDistanceMeters = 10.0,
            sessionState = TripSessionState.Stopped
        )

        assertNull(sut.pollSnapshotToSave(stopped))
    }

    @Test
    fun pollSnapshotToSave_whenUnchanged_returnsNullEvenAfterInterval() {
        now = 0L
        val sut = throttle(intervalMillis = 15_000L)

        sut.pollSnapshotToSave(runningSnapshot())
        now = 30_000L
        val result = sut.pollSnapshotToSave(runningSnapshot())

        assertNull(result)
    }
}

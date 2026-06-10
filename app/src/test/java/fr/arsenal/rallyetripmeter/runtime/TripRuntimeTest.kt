package fr.arsenal.rallyetripmeter.runtime

import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.persistence.PeriodicSaveThrottle
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateSnapshot
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateStore
import fr.arsenal.rallyetripmeter.domain.progress.TripProgressEngine
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TripRuntimeTest {
    @Test
    fun sessionAction_fromStopped_startsRunning() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Stopped)
        )

        runtime.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(TripSessionState.Running, runtime.state.sessionState)
    }

    @Test
    fun sessionAction_fromRunning_pauses() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(TripSessionState.Paused, runtime.state.sessionState)
    }

    @Test
    fun sessionAction_fromPaused_resumesRunning() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Paused)
        )

        runtime.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(TripSessionState.Running, runtime.state.sessionState)
    }

    @Test
    fun stop_keepsDistances_andStopsSession() {
        val runtime = TripRuntime(
            initialState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 3_000.0,
                partialDistanceMeters = 700.0
            )
        )

        runtime.onEvent(TripMeterUiEvent.Stop)

        assertEquals(TripSessionState.Stopped, runtime.state.sessionState)
        assertEquals(3_000.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(700.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun resetPartial_zeroesPartial_keepsTotal() {
        val runtime = TripRuntime(
            initialState = TripState(
                totalDistanceMeters = 3_000.0,
                partialDistanceMeters = 700.0
            )
        )

        runtime.onEvent(TripMeterUiEvent.ResetPartial)

        assertEquals(3_000.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(0.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun resetTotal_zeroesTotal_keepsPartial() {
        val runtime = TripRuntime(
            initialState = TripState(
                totalDistanceMeters = 3_000.0,
                partialDistanceMeters = 700.0
            )
        )

        runtime.onEvent(TripMeterUiEvent.ResetTotal)

        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(700.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun newRun_zeroesBothDistances_keepsSession() {
        val runtime = TripRuntime(
            initialState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 3_000.0,
                partialDistanceMeters = 700.0
            )
        )

        runtime.onEvent(TripMeterUiEvent.NewRun)

        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(0.0, runtime.state.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, runtime.state.sessionState)
    }

    @Test
    fun adjustPartialPlus10_addsTenMeters() {
        val runtime = TripRuntime(
            initialState = TripState(partialDistanceMeters = 700.0)
        )

        runtime.onEvent(TripMeterUiEvent.AdjustPartialPlus10)

        assertEquals(710.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun adjustPartialMinus10_subtractsTenMeters() {
        val runtime = TripRuntime(
            initialState = TripState(partialDistanceMeters = 700.0)
        )

        runtime.onEvent(TripMeterUiEvent.AdjustPartialMinus10)

        assertEquals(690.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_withoutPreviousSample_doesNotAccumulate() {
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    )
                )
            ),
            progressEngine = FakeTripProgressEngine(
                resultState = TripState(
                    totalDistanceMeters = 3_000.0,
                    partialDistanceMeters = 700.0
                )
            )
        )

        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)

        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(0.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_withTwoSamples_accumulates() {
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8380, longitude = -0.5794)
                    )
                )
            ),
            progressEngine = FakeTripProgressEngine(
                resultState = TripState(
                    totalDistanceMeters = 3_000.0,
                    partialDistanceMeters = 700.0,
                    gpsStatus = GpsStatus.Fixed
                )
            )
        )

        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)

        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals(3_000.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(700.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun applyLocationSample_propagatesGpsStatusAccuracyAndSpeed() {
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792),
                        accuracyMeters = 4.0,
                        speedMetersPerSecond = 12.0
                    )
                )
            )
        )

        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)

        assertEquals(GpsStatus.Fixed, runtime.state.gpsStatus)
        assertEquals(4.0, runtime.state.accuracyMeters!!, 0.0)
        assertEquals(12.0, runtime.state.speedMetersPerSecond!!, 0.0)
    }

    @Test
    fun controlEvent_isPersistedImmediately() {
        val store = FakeTripStateStore()
        val runtime = TripRuntime(
            tripStateStore = store,
            initialState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 3_000.0,
                partialDistanceMeters = 700.0
            )
        )

        runtime.onEvent(TripMeterUiEvent.Stop)

        assertEquals(1, store.savedSnapshots.size)
        assertEquals(TripSessionState.Stopped, store.savedSnapshots.last().sessionState)
    }

    @Test
    fun applyLocationSample_isPersistedAccordingToThrottle() {
        var now = 0L
        val store = FakeTripStateStore()
        val runtime = TripRuntime(
            progressEngine = FakeTripProgressEngine(
                resultState = TripState(
                    sessionState = TripSessionState.Running,
                    totalDistanceMeters = 3_000.0,
                    partialDistanceMeters = 700.0
                )
            ),
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8380, longitude = -0.5794)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8382, longitude = -0.5796)
                    )
                )
            ),
            tripStateStore = store,
            periodicSaveThrottle = PeriodicSaveThrottle(
                nowMillis = { now }
            ),
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        // Premier sample (sans précédent) : première sauvegarde throttle autorisée.
        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals(1, store.savedSnapshots.size)

        // Deuxième sample dans le même intervalle : throttle bloque.
        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals(1, store.savedSnapshots.size)

        // Au-delà de l'intervalle, avec snapshot modifié : nouvelle sauvegarde.
        now = 20_000L
        runtime.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals(2, store.savedSnapshots.size)
    }

    @Test
    fun persistCurrentState_savesExplicitly() {
        val store = FakeTripStateStore()
        val runtime = TripRuntime(
            tripStateStore = store,
            initialState = TripState(
                sessionState = TripSessionState.Stopped,
                totalDistanceMeters = 3_000.0,
                partialDistanceMeters = 700.0
            )
        )

        runtime.persistCurrentState()

        assertEquals(1, store.savedSnapshots.size)
        assertEquals(3_000.0, store.savedSnapshots.last().totalDistanceMeters, 0.0)
        assertEquals(700.0, store.savedSnapshots.last().partialDistanceMeters, 0.0)
    }

    private class FakeTripProgressEngine(
        private val resultState: TripState
    ) : TripProgressEngine {
        override fun applyLocationSample(
            state: TripState,
            previousSample: LocationSample?,
            currentSample: LocationSample
        ): TripState {
            return resultState
        }
    }

    private class FakeLocationEngine(
        private val gpsStatus: GpsStatus,
        private val samples: List<LocationSample?> = emptyList()
    ) : LocationEngine {
        private var index = 0

        override fun getGpsStatus(): GpsStatus {
            return gpsStatus
        }

        override fun getLastLocationSample(): LocationSample? {
            if (index >= samples.size) {
                return null
            }

            val sample = samples[index]
            index += 1

            return sample
        }
    }

    private class FakeTripStateStore : TripStateStore {
        val savedSnapshots = mutableListOf<TripStateSnapshot>()

        override fun save(snapshot: TripStateSnapshot) {
            savedSnapshots.add(snapshot)
        }

        override fun load(): TripStateSnapshot? {
            return savedSnapshots.lastOrNull()
        }
    }
}

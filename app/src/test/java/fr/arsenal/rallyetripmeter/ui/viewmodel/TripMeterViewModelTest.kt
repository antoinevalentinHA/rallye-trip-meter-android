package fr.arsenal.rallyetripmeter.ui.viewmodel

import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.permission.LocationPermissionState
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateSnapshot
import fr.arsenal.rallyetripmeter.domain.persistence.TripStateStore
import fr.arsenal.rallyetripmeter.domain.progress.TripProgressEngine
import fr.arsenal.rallyetripmeter.runtime.TripRuntime
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import fr.arsenal.rallyetripmeter.ui.model.UiSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TripMeterViewModelTest {
    @Test
    fun initialState_withInjectedLocationEngine_exposesInjectedGpsStatus() {
        val viewModel = TripMeterViewModel(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Searching
            )
        )

        val state = viewModel.uiState

        assertEquals("GPS RECHERCHE", state.gpsStatusText)
    }

    @Test
    fun initialState_withInjectedTripState_exposesInjectedDistancesAndSession() {
        val viewModel = TripMeterViewModel(
            initialTripState = TripState(
                totalDistanceMeters = 42_000.0,
                partialDistanceMeters = 1_230.0,
                gpsStatus = GpsStatus.Searching,
                sessionState = TripSessionState.Stopped
            )
        )

        val state = viewModel.uiState

        assertEquals("1.23 km", state.partialDistanceText)
        assertEquals("42.00 km", state.totalDistanceText)
        assertEquals("ARRÊTÉ", state.sessionStatusText)
        assertEquals("START", state.sessionActionText)
    }

    @Test
    fun initialState_isStoppedWithNeutralDistances() {
        val viewModel = TripMeterViewModel()

        val state = viewModel.uiState

        assertEquals("0.00 km", state.partialDistanceText)
        assertEquals("0.00 km", state.totalDistanceText)
        assertEquals("ARRÊTÉ", state.sessionStatusText)
        assertEquals("START", state.sessionActionText)
    }

    @Test
    fun initialState_exposesUnknownLocationPermissionStatus() {
        val viewModel = TripMeterViewModel()

        val state = viewModel.uiState

        assertEquals("POSITION ?", state.locationPermissionStatusText)
    }

    @Test
    fun initialState_withGrantedLocationPermission_exposesGrantedStatus() {
        val viewModel = TripMeterViewModel(
            initialLocationPermissionState = LocationPermissionState.Granted
        )

        val state = viewModel.uiState

        assertEquals("POSITION OK", state.locationPermissionStatusText)
    }

    @Test
    fun initialState_withDeniedLocationPermission_exposesDeniedStatus() {
        val viewModel = TripMeterViewModel(
            initialLocationPermissionState = LocationPermissionState.Denied
        )

        val state = viewModel.uiState

        assertEquals("POSITION REFUSÉE", state.locationPermissionStatusText)
    }

    @Test
    fun adjustPartialPlus10_increasesPartialDistanceText() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.AdjustPartialPlus10)

        assertEquals("0.01 km", viewModel.uiState.partialDistanceText)
    }

    @Test
    fun adjustPartialMinus100_decreasesPartialDistanceText() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.AdjustPartialMinus100)

        assertEquals("0.00 km", viewModel.uiState.partialDistanceText)
    }

    @Test
    fun resetPartial_setsPartialDistanceTextToZero() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.ResetPartial)

        assertEquals("0.00 km", viewModel.uiState.partialDistanceText)
    }

    @Test
    fun sessionAction_fromRunning_pausesSession() {
        val viewModel = TripMeterViewModel(
            initialTripState = TripState(
                sessionState = TripSessionState.Running
            )
        )

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals("PAUSE", viewModel.uiState.sessionStatusText)
        assertEquals("REPRISE", viewModel.uiState.sessionActionText)
    }

    @Test
    fun sessionAction_fromPaused_resumesSession() {
        val viewModel = TripMeterViewModel(
            initialTripState = TripState(
                sessionState = TripSessionState.Paused
            )
        )

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals("ACTIF", viewModel.uiState.sessionStatusText)
        assertEquals("PAUSE", viewModel.uiState.sessionActionText)
    }

    @Test
    fun stop_stopsSessionAndDisablesStop() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.Stop)

        assertEquals("ARRÊTÉ", viewModel.uiState.sessionStatusText)
        assertEquals("START", viewModel.uiState.sessionActionText)
        assertEquals(false, viewModel.uiState.isStopEnabled)
    }

    @Test
    fun sessionAction_fromStopped_startsSession() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.Stop)
        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals("ACTIF", viewModel.uiState.sessionStatusText)
        assertEquals("PAUSE", viewModel.uiState.sessionActionText)
    }

    @Test
    fun options_keepsStateUnchanged() {
        val viewModel = TripMeterViewModel()
        val initialState = viewModel.uiState

        viewModel.onEvent(TripMeterUiEvent.Options)

        assertEquals(initialState, viewModel.uiState)
    }

    @Test
    fun locationLifecycle_delegatesStartAndStopHandles() {
        var startCount = 0
        var stopCount = 0

        val viewModel = TripMeterViewModel(
            readLocationPermissionState = {
                LocationPermissionState.Granted
            },
            startLocationUpdates = {
                startCount += 1
            },
            stopLocationUpdates = {
                stopCount += 1
            }
        )

        viewModel.onStartLocation()
        viewModel.onStopLocation()

        assertEquals(1, startCount)
        assertEquals(1, stopCount)
    }

    @Test
    fun onStartLocation_withGrantedPermission_delegatesStartHandle() {
        var startCount = 0

        val viewModel = TripMeterViewModel(
            readLocationPermissionState = {
                LocationPermissionState.Granted
            },
            startLocationUpdates = {
                startCount += 1
            }
        )

        viewModel.onStartLocation()

        assertEquals(1, startCount)
        assertEquals("POSITION OK", viewModel.uiState.locationPermissionStatusText)
    }

    @Test
    fun onStartLocation_withDeniedPermission_doesNotDelegateStartHandle() {
        var startCount = 0

        val viewModel = TripMeterViewModel(
            readLocationPermissionState = {
                LocationPermissionState.Denied
            },
            startLocationUpdates = {
                startCount += 1
            }
        )

        viewModel.onStartLocation()

        assertEquals(0, startCount)
        assertEquals("POSITION REFUSÉE", viewModel.uiState.locationPermissionStatusText)
    }

    @Test
    fun applyLocationSample_refreshesGpsStatusFromLocationEngine() {
        val viewModel = TripMeterViewModel(
            initialTripState = TripState(
                gpsStatus = GpsStatus.Unavailable
            ),
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed
            )
        )

        viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)

        assertEquals("GPS OK", viewModel.uiState.gpsStatusText)
    }

    @Test
    fun applyLocationSample_withoutCurrentSample_keepsDistancesUnchanged() {
        val viewModel = TripMeterViewModel(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed
            )
        )

        val initialState = viewModel.uiState

        viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)

        assertEquals(initialState.partialDistanceText, viewModel.uiState.partialDistanceText)
        assertEquals(initialState.totalDistanceText, viewModel.uiState.totalDistanceText)
    }

    @Test
    fun applyLocationSample_withTwoSamples_usesInjectedProgressEngine() {
        val viewModel = TripMeterViewModel(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = fr.arsenal.rallyetripmeter.domain.geo.GeoPoint(
                            latitude = 44.8378,
                            longitude = -0.5792
                        )
                    ),
                    LocationSample(
                        point = fr.arsenal.rallyetripmeter.domain.geo.GeoPoint(
                            latitude = 44.8380,
                            longitude = -0.5794
                        )
                    )
                )
            ),
            progressEngine = FakeTripProgressEngine(
                resultState = TripState(
                    totalDistanceMeters = 3_000.0,
                    partialDistanceMeters = 700.0,
                    gpsStatus = GpsStatus.Fixed,
                    sessionState = TripSessionState.Running
                )
            )
        )

        viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals("0.00 km", viewModel.uiState.partialDistanceText)
        assertEquals("0.00 km", viewModel.uiState.totalDistanceText)

        viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)
        assertEquals("0.70 km", viewModel.uiState.partialDistanceText)
        assertEquals("3.00 km", viewModel.uiState.totalDistanceText)
    }

    @Test
    fun simulateLocationStep_whenRunning_increasesTotalAndPartialDistances() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.SessionAction)
        viewModel.onEvent(TripMeterUiEvent.SimulateLocationStep)

        assertEquals("0.03 km", viewModel.uiState.partialDistanceText)
        assertEquals("0.03 km", viewModel.uiState.totalDistanceText)
    }

    @Test
    fun simulateLocationStep_whenStopped_keepsDistancesUnchanged() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.Stop)
        val stoppedState = viewModel.uiState

        viewModel.onEvent(TripMeterUiEvent.SimulateLocationStep)

        assertEquals(stoppedState.partialDistanceText, viewModel.uiState.partialDistanceText)
        assertEquals(stoppedState.totalDistanceText, viewModel.uiState.totalDistanceText)
    }

    @Test
    fun simulateLocationStep_usesInjectedProgressEngine() {
        val viewModel = TripMeterViewModel(
            progressEngine = FakeTripProgressEngine(
                resultState = TripState(
                    totalDistanceMeters = 2_000.0,
                    partialDistanceMeters = 500.0,
                    gpsStatus = GpsStatus.Fixed,
                    sessionState = TripSessionState.Running
                )
            )
        )

        viewModel.onEvent(TripMeterUiEvent.SimulateLocationStep)

        assertEquals("0.50 km", viewModel.uiState.partialDistanceText)
        assertEquals("2.00 km", viewModel.uiState.totalDistanceText)
    }

    @Test
    fun onEvent_sessionAction_persistsSnapshot() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(tripStateStore = store)

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(1, store.savedSnapshots.size)
        assertEquals(TripSessionState.Running, store.savedSnapshots.last().sessionState)
    }

    @Test
    fun onEvent_stop_persistsSnapshot() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            initialTripState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 100.0,
                partialDistanceMeters = 20.0
            )
        )

        viewModel.onEvent(TripMeterUiEvent.Stop)

        assertEquals(1, store.savedSnapshots.size)
        assertEquals(TripSessionState.Stopped, store.savedSnapshots.last().sessionState)
    }

    @Test
    fun onEvent_resetPartial_persistsSnapshot() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            initialTripState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 100.0,
                partialDistanceMeters = 20.0
            )
        )

        viewModel.onEvent(TripMeterUiEvent.ResetPartial)

        assertEquals(1, store.savedSnapshots.size)
        assertEquals(0.0, store.savedSnapshots.last().partialDistanceMeters, 0.0)
    }

    @Test
    fun onEvent_adjustPartial_persistsDurableFieldsOnly() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            initialTripState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 100.0,
                partialDistanceMeters = 20.0
            )
        )

        viewModel.onEvent(TripMeterUiEvent.AdjustPartialPlus10)

        val snapshot = store.savedSnapshots.last()
        assertEquals(1, store.savedSnapshots.size)
        assertEquals(100.0, snapshot.totalDistanceMeters, 0.0)
        assertEquals(30.0, snapshot.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, snapshot.sessionState)
    }

    @Test
    fun onEvent_applyLocationSample_doesNotPersist() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            locationEngine = FakeLocationEngine(gpsStatus = GpsStatus.Fixed)
        )

        viewModel.onEvent(TripMeterUiEvent.ApplyLocationSample)

        assertEquals(0, store.savedSnapshots.size)
    }

    @Test
    fun persistCurrentState_savesSnapshot() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            initialTripState = TripState(
                sessionState = TripSessionState.Paused,
                totalDistanceMeters = 4_200.0,
                partialDistanceMeters = 350.0
            )
        )

        viewModel.persistCurrentState()

        val snapshot = store.savedSnapshots.last()
        assertEquals(1, store.savedSnapshots.size)
        assertEquals(4_200.0, snapshot.totalDistanceMeters, 0.0)
        assertEquals(350.0, snapshot.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Paused, snapshot.sessionState)
    }

    @Test
    fun onEvent_sessionBecomesRunning_withPermission_startsForegroundService() {
        var startCount = 0
        val viewModel = TripMeterViewModel(
            initialLocationPermissionState = LocationPermissionState.Granted,
            startForegroundService = { startCount += 1 }
        )

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(1, startCount)
    }

    @Test
    fun onEvent_sessionBecomesStopped_stopsForegroundService() {
        var stopCount = 0
        val viewModel = TripMeterViewModel(
            initialLocationPermissionState = LocationPermissionState.Granted,
            stopForegroundService = { stopCount += 1 },
            initialTripState = TripState(
                sessionState = TripSessionState.Running
            )
        )

        viewModel.onEvent(TripMeterUiEvent.Stop)

        assertEquals(1, stopCount)
    }

    @Test
    fun onEvent_sessionBecomesRunning_withoutPermission_doesNotStartForegroundService() {
        var startCount = 0
        val viewModel = TripMeterViewModel(
            initialLocationPermissionState = LocationPermissionState.Unknown,
            startForegroundService = { startCount += 1 }
        )

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(0, startCount)
    }

    @Test
    fun onEvent_sessionBecomesPaused_stopsForegroundService() {
        var stopCount = 0
        val viewModel = TripMeterViewModel(
            stopForegroundService = { stopCount += 1 },
            initialTripState = TripState(
                sessionState = TripSessionState.Running
            )
        )

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(1, stopCount)
    }

    @Test
    fun onEvent_sessionResumesToRunning_startsForegroundService() {
        var startCount = 0
        val viewModel = TripMeterViewModel(
            initialLocationPermissionState = LocationPermissionState.Granted,
            startForegroundService = { startCount += 1 },
            initialTripState = TripState(
                sessionState = TripSessionState.Paused
            )
        )

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals(1, startCount)
    }

    @Test
    fun onEvent_resetTotal_persistsSnapshot() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            initialTripState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 1234.0,
                partialDistanceMeters = 56.0
            )
        )

        viewModel.onEvent(TripMeterUiEvent.ResetTotal)

        val snapshot = store.savedSnapshots.last()
        assertEquals(1, store.savedSnapshots.size)
        assertEquals(0.0, snapshot.totalDistanceMeters, 0.0)
        assertEquals(56.0, snapshot.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, snapshot.sessionState)
    }

    @Test
    fun onEvent_newRun_persistsSnapshot() {
        val store = FakeTripStateStore()
        val viewModel = TripMeterViewModel(
            tripStateStore = store,
            initialTripState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 1234.0,
                partialDistanceMeters = 56.0
            )
        )

        viewModel.onEvent(TripMeterUiEvent.NewRun)

        val snapshot = store.savedSnapshots.last()
        assertEquals(1, store.savedSnapshots.size)
        assertEquals(0.0, snapshot.totalDistanceMeters, 0.0)
        assertEquals(0.0, snapshot.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, snapshot.sessionState)
    }

    @Test
    fun syncUiFromRuntime_reflectsRuntimeStateChange() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Stopped)
        )
        val viewModel = TripMeterViewModel(runtime = runtime)

        // Le runtime évolue hors onEvent : le miroir UI est encore obsolète.
        runtime.onEvent(TripMeterUiEvent.SessionAction)
        assertEquals(UiSessionStatus.Stopped, viewModel.uiState.sessionStatus)

        // Le tick de refresh resynchronise le miroir depuis le runtime.
        viewModel.syncUiFromRuntime()
        assertEquals(UiSessionStatus.Active, viewModel.uiState.sessionStatus)
    }

    @Test
    fun syncUiFromRuntime_isReadOnly_doesNotPersistOrTouchForegroundService() {
        var startCount = 0
        var stopCount = 0
        val store = FakeTripStateStore()
        val runtime = TripRuntime(
            tripStateStore = store,
            initialState = TripState(sessionState = TripSessionState.Running)
        )
        val viewModel = TripMeterViewModel(
            startForegroundService = { startCount += 1 },
            stopForegroundService = { stopCount += 1 },
            runtime = runtime
        )

        viewModel.syncUiFromRuntime()
        viewModel.syncUiFromRuntime()

        assertEquals(0, startCount)
        assertEquals(0, stopCount)
        assertEquals(0, store.savedSnapshots.size)
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

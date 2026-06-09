package fr.arsenal.rallyetripmeter.ui.viewmodel

import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import fr.arsenal.rallyetripmeter.domain.permission.LocationPermissionState
import org.junit.Assert.assertEquals
import org.junit.Test

class TripMeterViewModelTest {
    @Test
    fun initialState_isRunningWithBootstrapDistances() {
        val viewModel = TripMeterViewModel()

        val state = viewModel.uiState

        assertEquals("0.80 km", state.partialDistanceText)
        assertEquals("124.37 km", state.totalDistanceText)
        assertEquals("ACTIF", state.sessionStatusText)
        assertEquals("PAUSE", state.sessionActionText)
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

        assertEquals("0.81 km", viewModel.uiState.partialDistanceText)
    }

    @Test
    fun adjustPartialMinus100_decreasesPartialDistanceText() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.AdjustPartialMinus100)

        assertEquals("0.70 km", viewModel.uiState.partialDistanceText)
    }

    @Test
    fun resetPartial_setsPartialDistanceTextToZero() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.ResetPartial)

        assertEquals("0.00 km", viewModel.uiState.partialDistanceText)
    }

    @Test
    fun sessionAction_fromRunning_pausesSession() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.SessionAction)

        assertEquals("PAUSE", viewModel.uiState.sessionStatusText)
        assertEquals("REPRISE", viewModel.uiState.sessionActionText)
    }

    @Test
    fun sessionAction_fromPaused_resumesSession() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.SessionAction)
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
    fun simulateLocationStep_whenRunning_increasesTotalAndPartialDistances() {
        val viewModel = TripMeterViewModel()

        viewModel.onEvent(TripMeterUiEvent.SimulateLocationStep)

        assertEquals("0.83 km", viewModel.uiState.partialDistanceText)
        assertEquals("124.40 km", viewModel.uiState.totalDistanceText)
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
}

package fr.arsenal.rallyetripmeter.ui.viewmodel

import fr.arsenal.rallyetripmeter.domain.calibration.CalibrationCoefficient
import fr.arsenal.rallyetripmeter.domain.persistence.InMemoryCalibrationStore
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/*
 * ARSENAL RALLYE — Calibration wiring (palier 3)
 *
 * Vérifie :
 * - le coefficient par défaut (1.000) ;
 * - le chargement depuis le store à l'init ;
 * - les ajustements ±0.001 / ±0.010 et le reset, avec persistance ;
 * - que le mapper reçoit bien le coefficient (uiState.calibrationText) ;
 * - que les events de calibration sont UI-only (aucun event runtime).
 */
class TripMeterViewModelCalibrationTest {

    @Test
    fun calibration_defaultsToNeutral() {
        val viewModel = TripMeterViewModel(calibrationStore = InMemoryCalibrationStore())
        assertEquals("1.000", viewModel.uiState.calibrationText)
    }

    @Test
    fun calibration_loadedFromStoreAtInit() {
        val store = InMemoryCalibrationStore(CalibrationCoefficient.of(1020))
        val viewModel = TripMeterViewModel(calibrationStore = store)
        assertEquals("1.020", viewModel.uiState.calibrationText)
    }

    @Test
    fun calibration_plusSmall_updatesAndPersists() {
        val store = InMemoryCalibrationStore()
        val viewModel = TripMeterViewModel(calibrationStore = store)
        viewModel.onEvent(TripMeterUiEvent.AdjustCalibrationPlus1)
        assertEquals("1.001", viewModel.uiState.calibrationText)
        assertEquals(1001, store.load().perMille)
    }

    @Test
    fun calibration_plusLarge_updatesAndPersists() {
        val store = InMemoryCalibrationStore()
        val viewModel = TripMeterViewModel(calibrationStore = store)
        viewModel.onEvent(TripMeterUiEvent.AdjustCalibrationPlus10)
        assertEquals("1.010", viewModel.uiState.calibrationText)
        assertEquals(1010, store.load().perMille)
    }

    @Test
    fun calibration_minusSmall_updatesAndPersists() {
        val store = InMemoryCalibrationStore()
        val viewModel = TripMeterViewModel(calibrationStore = store)
        viewModel.onEvent(TripMeterUiEvent.AdjustCalibrationMinus1)
        assertEquals("0.999", viewModel.uiState.calibrationText)
        assertEquals(999, store.load().perMille)
    }

    @Test
    fun calibration_reset_returnsToNeutralAndPersists() {
        val store = InMemoryCalibrationStore(CalibrationCoefficient.of(1050))
        val viewModel = TripMeterViewModel(calibrationStore = store)
        viewModel.onEvent(TripMeterUiEvent.ResetCalibration)
        assertEquals("1.000", viewModel.uiState.calibrationText)
        assertEquals(1000, store.load().perMille)
    }

    @Test
    fun calibrationEvents_areUiOnly_andDoNotReachRuntime() {
        assertNull(TripMeterUiEvent.AdjustCalibrationMinus10.toTripRuntimeEvent())
        assertNull(TripMeterUiEvent.AdjustCalibrationMinus1.toTripRuntimeEvent())
        assertNull(TripMeterUiEvent.AdjustCalibrationPlus1.toTripRuntimeEvent())
        assertNull(TripMeterUiEvent.AdjustCalibrationPlus10.toTripRuntimeEvent())
        assertNull(TripMeterUiEvent.ResetCalibration.toTripRuntimeEvent())
    }
}

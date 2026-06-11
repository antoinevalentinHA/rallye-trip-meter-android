package fr.arsenal.rallyetripmeter.ui.viewmodel

import fr.arsenal.rallyetripmeter.runtime.TripRuntimeEvent
import fr.arsenal.rallyetripmeter.ui.model.TripMeterUiEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class TripMeterUiEventMappingTest {

    @Test
    fun toTripRuntimeEvent_mapsEachUiEventToItsRuntimeCounterpart() {
        assertEquals(
            TripRuntimeEvent.AdjustPartialMinus100,
            TripMeterUiEvent.AdjustPartialMinus100.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.AdjustPartialMinus10,
            TripMeterUiEvent.AdjustPartialMinus10.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.ResetPartial,
            TripMeterUiEvent.ResetPartial.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.AdjustPartialPlus10,
            TripMeterUiEvent.AdjustPartialPlus10.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.AdjustPartialPlus100,
            TripMeterUiEvent.AdjustPartialPlus100.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.SessionAction,
            TripMeterUiEvent.SessionAction.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.Stop,
            TripMeterUiEvent.Stop.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.ResetTotal,
            TripMeterUiEvent.ResetTotal.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.NewRun,
            TripMeterUiEvent.NewRun.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.Options,
            TripMeterUiEvent.Options.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.RefreshLocationPermission,
            TripMeterUiEvent.RefreshLocationPermission.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.ApplyLocationSample,
            TripMeterUiEvent.ApplyLocationSample.toTripRuntimeEvent()
        )
        assertEquals(
            TripRuntimeEvent.SimulateLocationStep,
            TripMeterUiEvent.SimulateLocationStep.toTripRuntimeEvent()
        )
    }
}

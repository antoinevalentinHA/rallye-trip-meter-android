package fr.arsenal.rallyetripmeter.ui.mapper

import fr.arsenal.rallyetripmeter.domain.permission.LocationPermissionState
import fr.arsenal.rallyetripmeter.ui.model.UiLocationPermissionStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPermissionMapperTest {
    @Test
    fun toUiLocationPermissionStatus_whenUnknown_returnsUnknown() {
        val result = LocationPermissionState.Unknown.toUiLocationPermissionStatus()

        assertEquals(UiLocationPermissionStatus.Unknown, result)
    }

    @Test
    fun toUiLocationPermissionStatus_whenGranted_returnsGranted() {
        val result = LocationPermissionState.Granted.toUiLocationPermissionStatus()

        assertEquals(UiLocationPermissionStatus.Granted, result)
    }

    @Test
    fun toUiLocationPermissionStatus_whenDenied_returnsDenied() {
        val result = LocationPermissionState.Denied.toUiLocationPermissionStatus()

        assertEquals(UiLocationPermissionStatus.Denied, result)
    }
}

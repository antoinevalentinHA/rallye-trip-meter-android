package fr.arsenal.rallyetripmeter.domain.distance

import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Test

class HaversineDistanceEngineTest {
    private val engine = HaversineDistanceEngine()

    @Test
    fun computeDistanceMeters_withSamePoint_returnsZero() {
        val point = GeoPoint(
            latitude = 44.8378,
            longitude = -0.5792
        )

        val result = engine.computeDistanceMeters(
            previous = point,
            current = point
        )

        assertEquals(0.0, result, 0.0001)
    }

    @Test
    fun computeDistanceMeters_withOneLongitudeDegreeAtEquator_returnsExpectedDistance() {
        val previous = GeoPoint(
            latitude = 0.0,
            longitude = 0.0
        )

        val current = GeoPoint(
            latitude = 0.0,
            longitude = 1.0
        )

        val result = engine.computeDistanceMeters(
            previous = previous,
            current = current
        )

        assertEquals(111_195.0, result, 1.0)
    }

    @Test
    fun computeDistanceMeters_betweenParisAndBordeaux_returnsExpectedOrderOfMagnitude() {
        val paris = GeoPoint(
            latitude = 48.8566,
            longitude = 2.3522
        )

        val bordeaux = GeoPoint(
            latitude = 44.8378,
            longitude = -0.5792
        )

        val result = engine.computeDistanceMeters(
            previous = paris,
            current = bordeaux
        )

        assertEquals(498_000.0, result, 5_000.0)
    }

    @Test
    fun computeDistanceMeters_isSymmetric() {
        val first = GeoPoint(
            latitude = 48.8566,
            longitude = 2.3522
        )

        val second = GeoPoint(
            latitude = 44.8378,
            longitude = -0.5792
        )

        val forwardDistance = engine.computeDistanceMeters(
            previous = first,
            current = second
        )

        val backwardDistance = engine.computeDistanceMeters(
            previous = second,
            current = first
        )

        assertEquals(forwardDistance, backwardDistance, 0.0001)
    }

    @Test
    fun computeDistanceMeters_ignoresAltitude() {
        val previousWithoutAltitude = GeoPoint(
            latitude = 44.8378,
            longitude = -0.5792,
            altitudeMeters = null
        )

        val currentWithoutAltitude = GeoPoint(
            latitude = 44.8379,
            longitude = -0.5793,
            altitudeMeters = null
        )

        val previousWithAltitude = previousWithoutAltitude.copy(
            altitudeMeters = 0.0
        )

        val currentWithAltitude = currentWithoutAltitude.copy(
            altitudeMeters = 1000.0
        )

        val distanceWithoutAltitude = engine.computeDistanceMeters(
            previous = previousWithoutAltitude,
            current = currentWithoutAltitude
        )

        val distanceWithAltitude = engine.computeDistanceMeters(
            previous = previousWithAltitude,
            current = currentWithAltitude
        )

        assertEquals(distanceWithoutAltitude, distanceWithAltitude, 0.0001)
    }
}


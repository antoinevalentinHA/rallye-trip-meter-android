package fr.arsenal.rallyetripmeter.runtime

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink
import fr.arsenal.rallyetripmeter.domain.geo.GeoPoint
import fr.arsenal.rallyetripmeter.domain.geo.LocationSample
import fr.arsenal.rallyetripmeter.domain.location.LocationEngine
import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import fr.arsenal.rallyetripmeter.domain.model.TripState
import fr.arsenal.rallyetripmeter.domain.progress.FilterResult
import fr.arsenal.rallyetripmeter.domain.progress.FilterState
import fr.arsenal.rallyetripmeter.domain.progress.GpsAccumulationFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * ARSENAL RALLYE — Runtime filter-state neutrality (P3.b)
 *
 * Objet :
 * - Verrouille les propriétés spécifiques au basculement P3.b, sans introduire
 *   de comportement nouveau :
 *   1. ApplyLocationSample et SimulateLocationStep empruntent le même contrat
 *      GpsAccumulationFilter.apply() ;
 *   2. l'ancre est portée par l'état filtre et avance même lorsqu'un échantillon
 *      est rejeté (bug conservé volontairement jusqu'en P4).
 *
 * Les neutralités de verdict/distance/log sont déjà couvertes par TripRuntimeTest
 * (verdicts, doublon, sans-échantillon) et par le replay bit-à-bit du corpus
 * (TickLogReplayHarnessTest) : ce fichier ne les duplique pas.
 */
class TripRuntimeFilterStateTest {

    @Test
    fun applyAndSimulate_bothRouteThroughTheSameFilter() {
        val recordingFilter = RecordingFilter()
        val runtime = TripRuntime(
            gpsAccumulationFilter = recordingFilter,
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    )
                )
            ),
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.SimulateLocationStep)

        // Les deux événements ont traversé le même point d'entrée filtre.
        assertEquals(2, recordingFilter.calls.size)

        // ApplyLocationSample : premier fix réel, sans ancre portée.
        assertEquals(null, recordingFilter.calls[0].anchor)

        // SimulateLocationStep : ancre transitoire fournie par le runtime
        // (l'échantillon simulé « précédent »), jamais l'ancre persistante.
        assertTrue(recordingFilter.calls[1].anchor != null)
    }

    @Test
    fun stationaryDrift_freezesAnchor_carriedByFilterState() {
        val sink = RecordingTickLogSink()
        val runtime = TripRuntime(
            // Vrai moteur : verdicts et distances réels, aucune simulation.
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    // A : premier fix -> IGNORED_NO_ANCHOR, l'ancre/centre devient A.
                    sampleAt(timestamp = 1_000L, lat = 44.8378, lon = -0.5792, speed = 0.2),
                    // B : ~13,6 m de A (< seuil de départ) -> dérive neutralisée (gate).
                    sampleAt(timestamp = 2_000L, lat = 44.8379, lon = -0.5793, speed = 0.2),
                    // C : ~37 m de A mais 1 seul fix > seuil (hystérésis 8 non atteinte)
                    // -> encore neutralisé. La machine reste STATIONARY tout du long.
                    sampleAt(timestamp = 3_000L, lat = 44.8381, lon = -0.5794, speed = 8.0)
                )
            ),
            tickLogSink = sink,
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(3, sink.entries.size)
        assertEquals(SampleVerdict.IGNORED_NO_ANCHOR, sink.entries[0].verdict)
        assertEquals(SampleVerdict.REJECTED_STATIONARY, sink.entries[1].verdict)
        assertEquals(SampleVerdict.REJECTED_STATIONARY, sink.entries[2].verdict)

        // Contrat P4.2 (inversé vs P4.a) : en STATIONARY, l'ancre NE suit PAS la
        // dérive. La référence du 3e tick reste l'ancre initiale A (t=1000), gelée
        // au centre — jamais le B rejeté (t=2000). Et rien n'est accumulé.
        assertEquals(1_000L, sink.entries[2].previousTimestampMillis)
        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    private fun sampleAt(
        timestamp: Long,
        lat: Double,
        lon: Double,
        speed: Double
    ): LocationSample {
        return LocationSample(
            point = GeoPoint(
                latitude = lat,
                longitude = lon,
                timestampMillis = timestamp
            ),
            speedMetersPerSecond = speed
        )
    }

    private data class RecordedCall(val anchor: LocationSample?, val current: LocationSample)

    /*
     * Filtre instrumenté : enregistre chaque appel apply() et renvoie un résultat
     * inerte (état métier inchangé). Sert uniquement à prouver le routage.
     */
    private class RecordingFilter : GpsAccumulationFilter {
        val calls = mutableListOf<RecordedCall>()

        override fun apply(
            tripState: TripState,
            filterState: FilterState,
            currentSample: LocationSample
        ): FilterResult {
            calls.add(RecordedCall(anchor = filterState.anchor, current = currentSample))
            return FilterResult(
                state = tripState,
                verdict = SampleVerdict.IGNORED_NO_ANCHOR,
                nextState = FilterState(anchor = currentSample)
            )
        }
    }

    private class RecordingTickLogSink : TickLogSink {
        val entries = mutableListOf<TickLogEntry>()

        override fun logMeta(meta: TickLogMeta) {
            // Hors périmètre des ticks.
        }

        override fun log(entry: TickLogEntry) {
            entries.add(entry)
        }

        override fun flush() {
            // No-op.
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
}

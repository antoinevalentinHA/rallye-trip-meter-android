package fr.arsenal.rallyetripmeter.runtime

import fr.arsenal.rallyetripmeter.domain.diag.SampleVerdict
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink
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
import fr.arsenal.rallyetripmeter.domain.progress.TripProgressResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TripRuntimeTest {
    @Test
    fun sessionAction_fromStopped_startsRunning() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Stopped)
        )

        runtime.onEvent(TripRuntimeEvent.SessionAction)

        assertEquals(TripSessionState.Running, runtime.state.sessionState)
    }

    @Test
    fun sessionAction_fromRunning_pauses() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.SessionAction)

        assertEquals(TripSessionState.Paused, runtime.state.sessionState)
    }

    @Test
    fun sessionAction_fromPaused_resumesRunning() {
        val runtime = TripRuntime(
            initialState = TripState(sessionState = TripSessionState.Paused)
        )

        runtime.onEvent(TripRuntimeEvent.SessionAction)

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

        runtime.onEvent(TripRuntimeEvent.Stop)

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

        runtime.onEvent(TripRuntimeEvent.ResetPartial)

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

        runtime.onEvent(TripRuntimeEvent.ResetTotal)

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

        runtime.onEvent(TripRuntimeEvent.NewRun)

        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(0.0, runtime.state.partialDistanceMeters, 0.0)
        assertEquals(TripSessionState.Running, runtime.state.sessionState)
    }

    @Test
    fun adjustPartialPlus10_addsTenMeters() {
        val runtime = TripRuntime(
            initialState = TripState(partialDistanceMeters = 700.0)
        )

        runtime.onEvent(TripRuntimeEvent.AdjustPartialPlus10)

        assertEquals(710.0, runtime.state.partialDistanceMeters, 0.0)
    }

    @Test
    fun adjustPartialMinus10_subtractsTenMeters() {
        val runtime = TripRuntime(
            initialState = TripState(partialDistanceMeters = 700.0)
        )

        runtime.onEvent(TripRuntimeEvent.AdjustPartialMinus10)

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

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

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

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
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

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

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

        runtime.onEvent(TripRuntimeEvent.Stop)

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
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        assertEquals(1, store.savedSnapshots.size)

        // Deuxième sample dans le même intervalle : throttle bloque.
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        assertEquals(1, store.savedSnapshots.size)

        // Au-delà de l'intervalle, avec snapshot modifié : nouvelle sauvegarde.
        now = 20_000L
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
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

    @Test
    fun applyLocationSample_repeatedTicksOnSameFix_doNotDoubleAccumulate() {
        // Baseline : un tick par fix (cadence simple).
        val single = TripRuntime(
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
            initialState = TripState(sessionState = TripSessionState.Running)
        )
        single.onEvent(TripRuntimeEvent.ApplyLocationSample)
        single.onEvent(TripRuntimeEvent.ApplyLocationSample)
        val baseline = single.state.totalDistanceMeters
        assertTrue(baseline > 0.0)

        // Double tick : chaque fix relu deux fois (pump UI + boucle service partageant
        // le meme runtime). Le previousLocationSample partage doit paver le trajet une
        // seule fois -> total identique a la baseline, aucune double accumulation.
        val doubled = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8380, longitude = -0.5794)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8380, longitude = -0.5794)
                    )
                )
            ),
            initialState = TripState(sessionState = TripSessionState.Running)
        )
        doubled.onEvent(TripRuntimeEvent.ApplyLocationSample)
        doubled.onEvent(TripRuntimeEvent.ApplyLocationSample)
        doubled.onEvent(TripRuntimeEvent.ApplyLocationSample)
        doubled.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(baseline, doubled.state.totalDistanceMeters, 0.0)
    }

    // ------------------------------------------------------------------
    // Observabilité P1.c : une TickLogEntry par tick ApplyLocationSample
    // ------------------------------------------------------------------

    @Test
    fun applyLocationSample_withoutSample_logsIgnoredNoSample_andKeepsDistances() {
        val sink = RecordingTickLogSink()
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Searching,
                samples = emptyList()
            ),
            tickLogSink = sink,
            nowMillis = { 42L },
            initialState = TripState(
                sessionState = TripSessionState.Running,
                totalDistanceMeters = 500.0,
                partialDistanceMeters = 50.0
            )
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(500.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(50.0, runtime.state.partialDistanceMeters, 0.0)
        assertEquals(1, sink.entries.size)
        val entry = sink.entries.single()
        assertEquals(SampleVerdict.IGNORED_NO_SAMPLE, entry.verdict)
        assertEquals(42L, entry.tickElapsedMillis)
        assertEquals(null, entry.latitude)
        assertEquals(null, entry.sampleIsNew)
        assertEquals(0.0, entry.deltaTotalMeters, 0.0)
        assertEquals(500.0, entry.totalMeters, 0.0)
        assertEquals(GpsStatus.Searching, entry.gpsStatus)
        assertEquals(TripSessionState.Running, entry.sessionState)
    }

    @Test
    fun applyLocationSample_onSameFixReread_logsIgnoredDuplicate_andKeepsDistances() {
        val sink = RecordingTickLogSink()
        val fix = LocationSample(
            point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
        )
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(fix, fix)
            ),
            tickLogSink = sink,
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(2, sink.entries.size)
        assertEquals(SampleVerdict.IGNORED_NO_ANCHOR, sink.entries[0].verdict)
        assertEquals(SampleVerdict.IGNORED_DUPLICATE, sink.entries[1].verdict)
        assertEquals(false, sink.entries[1].sampleIsNew)
        assertEquals(0.0, sink.entries[1].deltaTotalMeters, 0.0)
    }

    @Test
    fun applyLocationSample_firstSample_logsIgnoredNoAnchor() {
        val sink = RecordingTickLogSink()
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
            ),
            tickLogSink = sink,
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(1, sink.entries.size)
        val entry = sink.entries.single()
        assertEquals(SampleVerdict.IGNORED_NO_ANCHOR, entry.verdict)
        assertEquals(true, entry.sampleIsNew)
        assertEquals(44.8378, entry.latitude!!, 0.0)
        assertEquals(4.0, entry.accuracyMeters!!, 0.0)
        assertEquals(12.0, entry.speedMetersPerSecond!!, 0.0)
        assertEquals(null, entry.previousTimestampMillis)
    }

    @Test
    fun applyLocationSample_acceptedByEngine_logsEngineVerdictAndDelta() {
        val sink = RecordingTickLogSink()
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
            tickLogSink = sink,
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertTrue(runtime.state.totalDistanceMeters > 0.0)
        assertEquals(2, sink.entries.size)
        val accepted = sink.entries[1]
        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, accepted.verdict)
        assertEquals(true, accepted.sampleIsNew)
        assertEquals(
            runtime.state.totalDistanceMeters,
            accepted.deltaTotalMeters,
            0.0
        )
        assertEquals(
            runtime.state.totalDistanceMeters,
            accepted.totalMeters,
            0.0
        )
    }

    @Test
    fun applyLocationSample_rejectedByEngine_logsEngineVerdict_andKeepsDistances() {
        val sink = RecordingTickLogSink()
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792),
                        speedMetersPerSecond = 0.2
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8380, longitude = -0.5794),
                        speedMetersPerSecond = 0.2
                    )
                )
            ),
            tickLogSink = sink,
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(0.0, runtime.state.totalDistanceMeters, 0.0)
        assertEquals(2, sink.entries.size)
        val rejected = sink.entries[1]
        assertEquals(SampleVerdict.REJECTED_STATIONARY, rejected.verdict)
        assertEquals(0.0, rejected.deltaTotalMeters, 0.0)
        assertEquals(0.0, rejected.totalMeters, 0.0)
    }

    @Test
    fun applyLocationSample_emitsExactlyOneEntryPerTick() {
        val sink = RecordingTickLogSink()
        val runtime = TripRuntime(
            locationEngine = FakeLocationEngine(
                gpsStatus = GpsStatus.Fixed,
                samples = listOf(
                    null,
                    LocationSample(
                        point = GeoPoint(latitude = 44.8378, longitude = -0.5792)
                    ),
                    LocationSample(
                        point = GeoPoint(latitude = 44.8380, longitude = -0.5794)
                    )
                )
            ),
            tickLogSink = sink,
            initialState = TripState(sessionState = TripSessionState.Running)
        )

        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)
        runtime.onEvent(TripRuntimeEvent.ApplyLocationSample)

        assertEquals(3, sink.entries.size)
        assertEquals(SampleVerdict.IGNORED_NO_SAMPLE, sink.entries[0].verdict)
        assertEquals(SampleVerdict.IGNORED_NO_ANCHOR, sink.entries[1].verdict)
        assertEquals(SampleVerdict.ACCEPTED_SEGMENT, sink.entries[2].verdict)
    }

    private class RecordingTickLogSink : TickLogSink {
        val entries = mutableListOf<TickLogEntry>()

        override fun logMeta(meta: fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta) {
            // Hors périmètre des ticks.
        }

        override fun log(entry: TickLogEntry) {
            entries.add(entry)
        }

        override fun flush() {
            // No-op.
        }
    }

    private class FakeTripProgressEngine(
        private val resultState: TripState,
        private val verdict: SampleVerdict = SampleVerdict.ACCEPTED_SEGMENT
    ) : TripProgressEngine {
        override fun applyLocationSample(
            state: TripState,
            previousSample: LocationSample?,
            currentSample: LocationSample
        ): TripState {
            return applyLocationSampleWithVerdict(
                state = state,
                previousSample = previousSample,
                currentSample = currentSample
            ).state
        }

        override fun applyLocationSampleWithVerdict(
            state: TripState,
            previousSample: LocationSample?,
            currentSample: LocationSample
        ): TripProgressResult {
            return TripProgressResult(
                state = resultState,
                verdict = verdict
            )
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

package fr.arsenal.rallyetripmeter.domain.diag

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class TickLogJsonlTest {

    // ------------------------------------------------------------------
    // Round-trip
    // ------------------------------------------------------------------

    @Test
    fun roundTrip_entry_forEveryVerdict_preservesAllFields() {
        for (verdict in SampleVerdict.entries) {
            val entry = nominalEntry().copy(verdict = verdict)

            val decoded = TickLogJsonl.parseEntry(TickLogJsonl.encodeEntry(entry))

            assertEquals("Round-trip altéré pour $verdict", entry, decoded)
        }
    }

    @Test
    fun roundTrip_entry_withAllNullableFieldsNull_preservesNulls() {
        val entry = TickLogEntry(
            tickElapsedMillis = 0L,
            sampleTimestampMillis = null,
            sampleIsNew = null,
            latitude = null,
            longitude = null,
            accuracyMeters = null,
            speedMetersPerSecond = null,
            gpsStatus = GpsStatus.Unavailable,
            sessionState = TripSessionState.Stopped,
            previousTimestampMillis = null,
            segmentMeters = null,
            verdict = SampleVerdict.IGNORED_NO_SAMPLE,
            floorMeters = null,
            impliedSpeedKmh = null,
            deltaTotalMeters = 0.0,
            totalMeters = 0.0
        )

        val decoded = TickLogJsonl.parseEntry(TickLogJsonl.encodeEntry(entry))

        assertEquals(entry, decoded)
    }

    @Test
    fun roundTrip_entry_withExtremeValues_preservesExactDoubles() {
        val entry = nominalEntry().copy(
            latitude = -89.999_999_9,
            longitude = -179.999_999_9,
            accuracyMeters = 1.0E-7,
            speedMetersPerSecond = 0.0,
            segmentMeters = 12_345_678.901_234,
            impliedSpeedKmh = 1.0E9,
            deltaTotalMeters = 0.000_001,
            totalMeters = 9_999_999.999_999
        )

        val decoded = TickLogJsonl.parseEntry(TickLogJsonl.encodeEntry(entry))

        assertEquals(entry, decoded)
    }

    @Test
    fun roundTrip_meta_withCharactersRequiringEscaping_preservesText() {
        val meta = TickLogMeta(
            commitHash = "1d74bba",
            device = "Pixel \"8a\" \\ rev\nA\ttab",
            startedAtMillis = 1_781_200_000_000L
        )

        val encoded = TickLogJsonl.encodeMeta(meta)
        val decoded = TickLogJsonl.parseMeta(encoded)

        assertEquals(meta, decoded)
        assertFalse(
            "Une ligne JSONL ne doit contenir aucun saut de ligne brut.",
            encoded.contains('\n') || encoded.contains('\r')
        )
    }

    // ------------------------------------------------------------------
    // Forme des lignes
    // ------------------------------------------------------------------

    @Test
    fun encodeEntry_producesSingleVersionedLine() {
        val encoded = TickLogJsonl.encodeEntry(nominalEntry())

        assertTrue(
            "La ligne doit commencer par la version et le type.",
            encoded.startsWith("{\"v\":1,\"type\":\"tick\"")
        )
        assertFalse(encoded.contains('\n'))
        assertTrue(encoded.endsWith("}"))
    }

    @Test
    fun encodeMeta_producesSingleVersionedLine() {
        val encoded = TickLogJsonl.encodeMeta(
            TickLogMeta(
                commitHash = "abc",
                device = "device",
                startedAtMillis = 1L
            )
        )

        assertTrue(encoded.startsWith("{\"v\":1,\"type\":\"meta\""))
        assertFalse(encoded.contains('\n'))
    }

    // ------------------------------------------------------------------
    // Rejets à l'encodage
    // ------------------------------------------------------------------

    @Test
    fun encodeEntry_withNonFiniteDouble_isRejected() {
        val entry = nominalEntry().copy(accuracyMeters = Double.NaN)

        assertThrowsIllegalArgument("Double non fini accepté à tort.") {
            TickLogJsonl.encodeEntry(entry)
        }
    }

    // ------------------------------------------------------------------
    // Rejets au décodage
    // ------------------------------------------------------------------

    @Test
    fun parseEntry_withWrongVersion_isRejected() {
        val line = TickLogJsonl.encodeEntry(nominalEntry())
            .replaceFirst("\"v\":1", "\"v\":2")

        assertThrowsIllegalArgument("Version de schéma inattendue acceptée.") {
            TickLogJsonl.parseEntry(line)
        }
    }

    @Test
    fun parseEntry_withMetaLine_isRejected() {
        val metaLine = TickLogJsonl.encodeMeta(
            TickLogMeta(
                commitHash = "abc",
                device = "device",
                startedAtMillis = 1L
            )
        )

        assertThrowsIllegalArgument("Ligne meta acceptée comme tick.") {
            TickLogJsonl.parseEntry(metaLine)
        }
    }

    @Test
    fun parseMeta_withTickLine_isRejected() {
        val tickLine = TickLogJsonl.encodeEntry(nominalEntry())

        assertThrowsIllegalArgument("Ligne tick acceptée comme meta.") {
            TickLogJsonl.parseMeta(tickLine)
        }
    }

    @Test
    fun parseEntry_withMalformedLine_isRejected() {
        val malformedLines = listOf(
            "",
            "{",
            "not json",
            "{\"v\":1,\"type\":\"tick\"",
            TickLogJsonl.encodeEntry(nominalEntry()) + "garbage",
            "{\"v\":1,\"type\":\"tick\",\"tick_elapsed_ms\":}"
        )

        for (line in malformedLines) {
            assertThrowsIllegalArgument("Ligne malformée acceptée : '$line'") {
                TickLogJsonl.parseEntry(line)
            }
        }
    }

    @Test
    fun parseEntry_withMissingField_isRejected() {
        val line = TickLogJsonl.encodeEntry(nominalEntry())
            .replaceFirst("\"total_m\":", "\"renamed\":")

        assertThrowsIllegalArgument("Champ manquant accepté.") {
            TickLogJsonl.parseEntry(line)
        }
    }

    @Test
    fun parseEntry_withUnknownVerdict_isRejected() {
        val line = TickLogJsonl.encodeEntry(nominalEntry())
            .replaceFirst(
                "\"verdict\":\"${nominalEntry().verdict.name}\"",
                "\"verdict\":\"NOT_A_VERDICT\""
            )

        assertThrowsIllegalArgument("Verdict inconnu accepté.") {
            TickLogJsonl.parseEntry(line)
        }
    }

    @Test
    fun parseEntry_withUnknownSessionState_isRejected() {
        val line = TickLogJsonl.encodeEntry(nominalEntry())
            .replaceFirst(
                "\"session_state\":\"Running\"",
                "\"session_state\":\"Flying\""
            )

        assertThrowsIllegalArgument("État de session inconnu accepté.") {
            TickLogJsonl.parseEntry(line)
        }
    }

    // ------------------------------------------------------------------
    // Support
    // ------------------------------------------------------------------

    private fun nominalEntry(): TickLogEntry {
        return TickLogEntry(
            tickElapsedMillis = 12_000L,
            sampleTimestampMillis = 1_781_200_012_345L,
            sampleIsNew = true,
            latitude = 44.8378,
            longitude = -0.5792,
            accuracyMeters = 4.5,
            speedMetersPerSecond = 12.3,
            gpsStatus = GpsStatus.Fixed,
            sessionState = TripSessionState.Running,
            previousTimestampMillis = 1_781_200_011_345L,
            segmentMeters = 12.7,
            verdict = SampleVerdict.ACCEPTED_SEGMENT,
            floorMeters = 2.0,
            impliedSpeedKmh = 45.72,
            deltaTotalMeters = 12.7,
            totalMeters = 1_234.5
        )
    }

    private fun assertThrowsIllegalArgument(
        message: String,
        block: () -> Unit
    ) {
        try {
            block()
            fail(message)
        } catch (_: IllegalArgumentException) {
            // Attendu.
        }
    }
}

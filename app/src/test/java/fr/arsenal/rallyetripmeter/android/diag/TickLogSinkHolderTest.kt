package fr.arsenal.rallyetripmeter.android.diag

import fr.arsenal.rallyetripmeter.domain.diag.TickLogJsonl
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TickLogSinkHolderTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun resetHolder() {
        // Le holder est process-wide : chaque test restaure l'état no-op.
        TickLogSinkHolder.clearSessionSink()?.close()
    }

    @Test
    fun withoutSession_sinkIsNoOp_andClearReturnsNull() {
        assertFalse(TickLogSinkHolder.hasSessionSink())

        // Aucune session : les écritures ne produisent rien et n'échouent pas.
        TickLogSinkHolder.getSink().logMeta(sessionMeta())
        TickLogSinkHolder.getSink().flush()

        assertNull(TickLogSinkHolder.clearSessionSink())
    }

    @Test
    fun installSessionSink_routesWritesToFile() {
        val file = File(temporaryFolder.newFolder("gpslogs"), "gpslog_holder.jsonl")
        TickLogSinkHolder.installSessionSink(FileTickLogSink(file))

        assertTrue(TickLogSinkHolder.hasSessionSink())

        TickLogSinkHolder.getSink().logMeta(sessionMeta())
        TickLogSinkHolder.getSink().flush()

        val lines = file.readLines()
        assertEquals(1, lines.size)
        assertEquals(sessionMeta(), TickLogJsonl.parseMeta(lines[0]))
    }

    @Test
    fun clearSessionSink_returnsInstalledSink_andRestoresNoOp() {
        val file = File(temporaryFolder.newFolder("gpslogs"), "gpslog_clear.jsonl")
        val installed = FileTickLogSink(file)
        TickLogSinkHolder.installSessionSink(installed)
        TickLogSinkHolder.getSink().logMeta(sessionMeta())

        val cleared = TickLogSinkHolder.clearSessionSink()

        assertEquals(installed, cleared)
        assertFalse(TickLogSinkHolder.hasSessionSink())
        cleared?.close()
        assertEquals(1, file.readLines().size)

        // Après le retrait, le délégateur stable est redevenu silencieux.
        TickLogSinkHolder.getSink().logMeta(sessionMeta())
        assertEquals(1, file.readLines().size)
    }

    private fun sessionMeta(): TickLogMeta {
        return TickLogMeta(
            commitHash = "0.1.0",
            device = "JVM test",
            startedAtMillis = 1_781_200_000_000L
        )
    }
}

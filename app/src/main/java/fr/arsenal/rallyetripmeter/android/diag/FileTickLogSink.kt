package fr.arsenal.rallyetripmeter.android.diag

import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogJsonl
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.FileWriter
import java.io.IOException

/*
 * ARSENAL RALLYE — File tick log sink
 *
 * Rôle :
 * - Persiste le journal d'observabilité en JSONL (une ligne par entrée)
 *   dans un fichier de session.
 *
 * Contraintes :
 * - java.io uniquement : aucun import Android, testable en JVM pure.
 * - Encodage exclusivement via TickLogJsonl (schéma v1, aucune dépendance JSON).
 * - L'observabilité ne doit jamais faire échouer une session : toute IOException
 *   est avalée, le sink se neutralise silencieusement.
 *
 * Principe :
 * - Écriture bufferisée ; le chemin chaud (un log par tick à 1 Hz) ne touche
 *   que le tampon mémoire.
 * - Flush automatique toutes les FLUSH_EVERY_LINES lignes (durabilité ~30 s
 *   à 1 Hz), flush explicite à la demande, close idempotent avec flush final.
 * - Après close (ou après défaillance), toute écriture est ignorée sans erreur.
 */
class FileTickLogSink(
    file: File
) : TickLogSink, Closeable {
    @Volatile
    private var writer: BufferedWriter? = try {
        BufferedWriter(FileWriter(file, true), BUFFER_SIZE_BYTES)
    } catch (_: IOException) {
        null
    }

    private var linesSinceFlush: Int = 0

    override fun logMeta(meta: TickLogMeta) {
        writeLine(TickLogJsonl.encodeMeta(meta))
    }

    override fun log(entry: TickLogEntry) {
        writeLine(TickLogJsonl.encodeEntry(entry))
    }

    override fun flush() {
        val activeWriter = writer ?: return
        try {
            activeWriter.flush()
            linesSinceFlush = 0
        } catch (_: IOException) {
            neutralize()
        }
    }

    override fun close() {
        val activeWriter = writer ?: return
        writer = null
        try {
            activeWriter.flush()
            activeWriter.close()
        } catch (_: IOException) {
            // Défensif : la fermeture ne doit jamais propager.
        }
    }

    private fun writeLine(line: String) {
        val activeWriter = writer ?: return
        try {
            activeWriter.write(line)
            activeWriter.newLine()
            linesSinceFlush += 1
            if (linesSinceFlush >= FLUSH_EVERY_LINES) {
                activeWriter.flush()
                linesSinceFlush = 0
            }
        } catch (_: IOException) {
            neutralize()
        }
    }

    private fun neutralize() {
        val failedWriter = writer ?: return
        writer = null
        try {
            failedWriter.close()
        } catch (_: IOException) {
            // Déjà en échec : rien à faire.
        }
    }

    private companion object {
        const val BUFFER_SIZE_BYTES = 64 * 1024
        const val FLUSH_EVERY_LINES = 30
    }
}

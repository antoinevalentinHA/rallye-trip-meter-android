package fr.arsenal.rallyetripmeter.replay

import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogJsonl
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import java.io.File

/*
 * ARSENAL RALLYE — Tick log replay reader (outillage P2.a, sources de test)
 *
 * Rôle :
 * - Lit un fichier JSONL P1 (schéma v1) et le matérialise en méta + entrées,
 *   prêt pour l'analyse et la ré-exécution.
 *
 * Contraintes :
 * - Pur Kotlin/JVM, aucune dépendance Android ni JSON.
 * - Décodage exclusivement via TickLogJsonl (un seul codec dans le dépôt).
 * - Déterministe : même fichier -> même résultat, ordre du fichier préservé.
 *
 * Principe :
 * - Strict ligne à ligne ; les lignes indécodables (ex. dernière ligne
 *   tronquée par un arrêt brutal entre deux flushs, fichier sans méta)
 *   ne font pas échouer la lecture : elles sont comptées et localisées,
 *   car leur existence fait partie de la preuve.
 * - Les lignes vides finales sont ignorées silencieusement.
 */
object TickLogReplayReader {
    data class ParsedTickLog(
        val meta: TickLogMeta?,
        val entries: List<IndexedEntry>,
        val malformedLines: List<MalformedLine>,
    ) {
        val tickCount: Int get() = entries.size
    }

    data class IndexedEntry(
        /** Numéro de ligne dans le fichier source (1-indexé). */
        val lineNumber: Int,
        val entry: TickLogEntry,
    )

    data class MalformedLine(
        val lineNumber: Int,
        val reason: String,
    )

    fun parseLines(lines: List<String>): ParsedTickLog {
        var meta: TickLogMeta? = null
        val entries = mutableListOf<IndexedEntry>()
        val malformed = mutableListOf<MalformedLine>()

        lines.forEachIndexed { index, rawLine ->
            val lineNumber = index + 1
            val line = rawLine.trimEnd('\r')

            if (line.isBlank()) {
                return@forEachIndexed
            }

            val entryFailure = try {
                entries.add(
                    IndexedEntry(
                        lineNumber = lineNumber,
                        entry = TickLogJsonl.parseEntry(line)
                    )
                )
                return@forEachIndexed
            } catch (exception: IllegalArgumentException) {
                exception.message ?: "ligne tick indécodable"
            }

            try {
                val parsedMeta = TickLogJsonl.parseMeta(line)
                if (meta == null) {
                    meta = parsedMeta
                } else {
                    malformed.add(
                        MalformedLine(
                            lineNumber = lineNumber,
                            reason = "méta dupliquée"
                        )
                    )
                }
            } catch (_: IllegalArgumentException) {
                malformed.add(
                    MalformedLine(
                        lineNumber = lineNumber,
                        reason = entryFailure
                    )
                )
            }
        }

        return ParsedTickLog(
            meta = meta,
            entries = entries,
            malformedLines = malformed
        )
    }

    fun parseFile(file: File): ParsedTickLog {
        return parseLines(file.readLines())
    }

    fun parseResource(resourcePath: String): ParsedTickLog {
        val stream = TickLogReplayReader::class.java.classLoader
            .getResourceAsStream(resourcePath)
            ?: throw IllegalArgumentException(
                "Ressource introuvable : '$resourcePath'."
            )

        return stream.bufferedReader().use { reader ->
            parseLines(reader.readLines())
        }
    }
}

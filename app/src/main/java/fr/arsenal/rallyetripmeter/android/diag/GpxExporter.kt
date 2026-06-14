package fr.arsenal.rallyetripmeter.android.diag

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import fr.arsenal.rallyetripmeter.domain.diag.GpxWriter
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogJsonl
import java.io.File

/*
 * ARSENAL RALLYE — GPX exporter (a posteriori)
 *
 * Rôle :
 * - À partir du fichier JSONL de session DÉJÀ écrit, produit un GPX de la trace
 *   parcourue et l'écrit dans Downloads/gpslogs via MediaStore (même dossier que
 *   les JSONL, accessible depuis tout gestionnaire de fichiers et depuis Termux).
 *
 * Quand :
 * - Déclenché UNIQUEMENT à la fin de session (onDestroy du service), donc
 *   strictement a posteriori. Jamais pendant une session active : aucune trace
 *   n'est produite ni affichée en cours de rallye (cf. contrat fonctionnel,
 *   « Interdiction ferme »).
 *
 * Contraintes :
 * - Infrastructure uniquement : aucune logique métier, aucun verdict, aucune
 *   distance. Ne réacquiert aucune position : il dérive la trace du JSONL existant.
 * - Aucune dépendance ajoutée. Réutilise TickLogJsonl (parse) et GpxWriter (domaine).
 * - Toute défaillance retourne false sans exception : l'export ne doit jamais
 *   faire échouer l'arrêt du service.
 *
 * Principe :
 * - Android 10+ : insertion MediaStore (RELATIVE_PATH Downloads/gpslogs).
 * - Android 9 (minSdk 28) : pas d'export (cohérent avec TickLogExporter).
 * - Nom du GPX : même base que le JSONL, extension .gpx (gpslog_*.gpx).
 * - Si la session ne contient aucun point exploitable : aucun fichier créé.
 */
object GpxExporter {

    fun exportSessionTrace(
        context: Context,
        jsonlFile: File
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        if (!jsonlFile.isFile || jsonlFile.length() == 0L) {
            return false
        }

        return try {
            val entries = parseEntries(jsonlFile)
            val gpx = GpxWriter.toGpx(entries, trackName = jsonlFile.nameWithoutExtension)
                ?: return false

            val displayName = jsonlFile.nameWithoutExtension + GPX_EXTENSION

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, MIME_TYPE)
                put(
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_SUBDIRECTORY
                )
            }

            val resolver = context.contentResolver
            val target = resolver.insert(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                values
            ) ?: return false

            resolver.openOutputStream(target)?.use { output ->
                output.write(gpx.toByteArray(Charsets.UTF_8))
            } ?: return false

            true
        } catch (_: Exception) {
            false
        }
    }

    private fun parseEntries(jsonlFile: File): List<TickLogEntry> {
        val entries = ArrayList<TickLogEntry>()
        jsonlFile.useLines { lines ->
            for (rawLine in lines) {
                val line = rawLine.trim()
                if (line.isEmpty()) {
                    continue
                }
                // Seules les lignes 'tick' portent lat/lon ; la ligne 'meta' et toute
                // ligne malformée sont ignorées (l'export ne doit jamais échouer).
                val entry = runCatching { TickLogJsonl.parseEntry(line) }.getOrNull()
                if (entry != null) {
                    entries.add(entry)
                }
            }
        }
        return entries
    }

    private const val EXPORT_SUBDIRECTORY = "gpslogs"
    private const val GPX_EXTENSION = ".gpx"
    private const val MIME_TYPE = "application/gpx+xml"
}

package fr.arsenal.rallyetripmeter.android.diag

import android.content.Context
import fr.arsenal.rallyetripmeter.domain.diag.GpxTrack
import fr.arsenal.rallyetripmeter.domain.diag.GpxTrackReader
import java.io.File

/*
 * ARSENAL RALLYE — Source locale des traces exportées (relecture a posteriori)
 *
 * Rôle :
 * - Liste les fichiers .gpx du répertoire INTERNE de l'app
 *   (getExternalFilesDir("gpslogs")), triés par date de modification décroissante.
 * - Lit le contenu d'un fichier .gpx et le parse en GpxTrack (domaine pur).
 *
 * Garde-fous / contraintes :
 * - Lit UNIQUEMENT des fichiers déjà écrits ; n'accède jamais au LocationEngine,
 *   au service, ni à la position courante. Aucune acquisition GPS possible ici.
 * - Répertoire interne de l'app : AUCUNE permission de stockage requise.
 * - Aucune dépendance réseau, aucune bibliothèque externe.
 */
object TraceFileSource {

    data class TraceFile(
        val file: File,
        val displayName: String,
        val lastModifiedMillis: Long,
    )

    /** Liste les .gpx du dossier interne gpslogs, plus récent d'abord. */
    fun listTraces(context: Context): List<TraceFile> {
        val directory = resolveDirectory(context) ?: return emptyList()
        val files = directory.listFiles { candidate ->
            candidate.isFile && candidate.name.endsWith(GPX_EXTENSION, ignoreCase = true)
        } ?: return emptyList()

        return files
            .map {
                TraceFile(
                    file = it,
                    displayName = it.name,
                    lastModifiedMillis = it.lastModified()
                )
            }
            .sortedByDescending { it.lastModifiedMillis }
    }

    /** Lit et parse une trace. Retourne une trace vide si lecture impossible. */
    fun readTrack(traceFile: TraceFile): GpxTrack {
        return try {
            val content = traceFile.file.readText(Charsets.UTF_8)
            GpxTrackReader.parse(content)
        } catch (_: Exception) {
            GpxTrack(name = null, points = emptyList())
        }
    }

    private fun resolveDirectory(context: Context): File? {
        val external = context.getExternalFilesDir(LOG_DIRECTORY_NAME)
        if (external != null) {
            return external
        }
        // Repli cohérent avec TickLogSessionFactory.
        return File(context.filesDir, LOG_DIRECTORY_NAME).takeIf { it.isDirectory }
    }

    private const val LOG_DIRECTORY_NAME = "gpslogs"
    private const val GPX_EXTENSION = ".gpx"
}

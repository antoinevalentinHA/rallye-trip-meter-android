package fr.arsenal.rallyetripmeter.android.diag

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/*
 * ARSENAL RALLYE — Tick log exporter
 *
 * Rôle :
 * - Copie le fichier de log de session vers le stockage public
 *   Downloads/gpslogs via MediaStore, pour un accès direct depuis tout
 *   gestionnaire de fichiers et depuis Termux (~/storage/downloads).
 *
 * Contraintes :
 * - Infrastructure uniquement : aucune logique métier, aucun verdict.
 * - Aucune permission requise : MediaStore.Downloads accepte les insertions
 *   de l'app propriétaire à partir d'Android 10 (API 29).
 * - Toute défaillance retourne false sans exception : l'export ne doit
 *   jamais faire échouer l'arrêt du service.
 *
 * Principe :
 * - Android 10+ : insertion MediaStore (RELATIVE_PATH Downloads/gpslogs).
 * - Android 9 (minSdk 28) : pas d'export — le répertoire applicatif externe
 *   est encore librement accessible avant la restriction d'Android 11,
 *   et MediaStore.Downloads n'existe pas en API 28.
 * - Le fichier source reste en place : Downloads reçoit une copie.
 */
object TickLogExporter {
    fun exportToDownloads(
        context: Context,
        file: File
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        if (!file.isFile || file.length() == 0L) {
            return false
        }

        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
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
                file.inputStream().use { input ->
                    input.copyTo(output)
                }
            } ?: return false

            true
        } catch (_: Exception) {
            false
        }
    }

    private const val EXPORT_SUBDIRECTORY = "gpslogs"
    private const val MIME_TYPE = "text/plain"
}

package fr.arsenal.rallyetripmeter.android.diag

import android.content.Context
import android.os.Build
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * ARSENAL RALLYE — Tick log session factory
 *
 * Rôle :
 * - Crée le sink de session : résolution du répertoire, nommage du fichier,
 *   écriture de la ligne meta en tête (TickLogMeta).
 *
 * Contraintes :
 * - Seule pièce du sous-système d'observabilité dépendant d'un Context.
 * - Aucune logique métier GPS, aucune écriture de tick (concern du runtime).
 * - Toute défaillance retourne null : l'appelant retombe sur le no-op,
 *   jamais d'exception propagée.
 *
 * Principe :
 * - Répertoire : <external files>/gpslogs (lisible depuis le téléphone via
 *   l'app Fichiers / Termux, sans adb, supprimé à la désinstallation) ;
 *   repli sur <files>/gpslogs si le stockage externe est indisponible.
 * - Nommage : gpslog_yyyyMMdd_HHmmss.jsonl, suffixe _n en cas de collision.
 * - Meta : version applicative (PackageManager), modèle d'appareil, horodatage.
 */
object TickLogSessionFactory {
    fun createSessionSink(
        context: Context,
        startedAtMillis: Long = System.currentTimeMillis()
    ): FileTickLogSink? {
        val directory = resolveLogDirectory(context) ?: return null

        val file = resolveSessionFile(directory, startedAtMillis) ?: return null

        val sink = FileTickLogSink(file)

        sink.logMeta(
            TickLogMeta(
                commitHash = applicationVersionName(context),
                device = Build.MODEL ?: UNKNOWN_VALUE,
                startedAtMillis = startedAtMillis
            )
        )
        sink.flush()

        return sink
    }

    fun logDirectoryPath(context: Context): String? {
        return resolveLogDirectory(context)?.absolutePath
    }

    private fun resolveLogDirectory(context: Context): File? {
        val externalDirectory = try {
            context.getExternalFilesDir(LOG_DIRECTORY_NAME)
        } catch (_: Exception) {
            null
        }

        val directory = externalDirectory
            ?: File(context.filesDir, LOG_DIRECTORY_NAME)

        if (directory.exists() || directory.mkdirs()) {
            return directory
        }

        return null
    }

    private fun resolveSessionFile(
        directory: File,
        startedAtMillis: Long
    ): File? {
        val baseName = FILE_PREFIX + SimpleDateFormat(
            FILE_TIMESTAMP_PATTERN,
            Locale.US
        ).format(Date(startedAtMillis))

        val candidate = File(directory, "$baseName$FILE_EXTENSION")
        if (!candidate.exists()) {
            return candidate
        }

        for (suffix in 1..MAX_COLLISION_SUFFIX) {
            val deduplicated = File(directory, "${baseName}_$suffix$FILE_EXTENSION")
            if (!deduplicated.exists()) {
                return deduplicated
            }
        }

        return null
    }

    private fun applicationVersionName(context: Context): String {
        return try {
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName
                ?: UNKNOWN_VALUE
        } catch (_: Exception) {
            UNKNOWN_VALUE
        }
    }

    private const val LOG_DIRECTORY_NAME = "gpslogs"
    private const val FILE_PREFIX = "gpslog_"
    private const val FILE_TIMESTAMP_PATTERN = "yyyyMMdd_HHmmss"
    private const val FILE_EXTENSION = ".jsonl"
    private const val MAX_COLLISION_SUFFIX = 99
    private const val UNKNOWN_VALUE = "unknown"
}

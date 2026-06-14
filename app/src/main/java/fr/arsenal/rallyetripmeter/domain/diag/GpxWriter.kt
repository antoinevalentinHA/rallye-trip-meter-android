package fr.arsenal.rallyetripmeter.domain.diag

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/*
 * ARSENAL RALLYE — GPX writer (export a posteriori de la trace)
 *
 * Rôle :
 * - Sérialise une séquence de TickLogEntry en un document GPX 1.1 (trk/trkseg/trkpt).
 * - C'est un artefact de SORTIE a posteriori (après session terminée), un souvenir
 *   du parcours. Il ne participe JAMAIS au calcul du trip meter et n'est jamais
 *   produit ni affiché pendant une session active (cf. contrat fonctionnel,
 *   « Interdiction ferme »). Ce writer ne fait que transformer des points déjà
 *   capturés ; il n'acquiert aucune position et ne touche à aucune distance.
 *
 * Contraintes :
 * - Domaine pur : aucune dépendance Android, aucune dépendance XML externe.
 *   Testable intégralement en JVM.
 * - Aucun calcul de distance, aucun verdict, aucune logique métier.
 *
 * Principe :
 * - Un seul <trk> contenant un seul <trkseg>.
 * - Un <trkpt lat lon> par échantillon EXPLOITABLE : latitude/longitude non nulles
 *   et dans les bornes valides. Les points invalides sont ignorés silencieusement.
 * - <time> (ISO 8601 UTC) ajouté si sampleTimestampMillis est présent.
 * - <ele> n'est PAS émis : l'altitude n'est pas portée par le schéma JSONL v1.
 *   GPX la rend optionnelle ; son absence est conforme.
 * - Échappement XML systématique des valeurs textuelles (métadonnées).
 */
object GpxWriter {

    /** Vrai si la séquence contient au moins un point traçable. */
    fun hasTrackablePoints(entries: List<TickLogEntry>): Boolean {
        return entries.any { it.isTrackable() }
    }

    /**
     * Produit le document GPX. Retourne null si aucun point exploitable
     * (aucune trace à exporter — l'appelant ne crée alors pas de fichier).
     *
     * @param trackName nom du <trk> (échappé) ; libellé de session lisible.
     */
    fun toGpx(
        entries: List<TickLogEntry>,
        trackName: String = DEFAULT_TRACK_NAME
    ): String? {
        val points = entries.filter { it.isTrackable() }
        if (points.isEmpty()) {
            return null
        }

        val builder = StringBuilder()
        builder.append(XML_PROLOG).append('\n')
        builder.append(GPX_OPEN).append('\n')
        builder.append("  <trk>\n")
        builder.append("    <name>").append(escapeXml(trackName)).append("</name>\n")
        builder.append("    <trkseg>\n")

        for (point in points) {
            appendTrackPoint(builder, point)
        }

        builder.append("    </trkseg>\n")
        builder.append("  </trk>\n")
        builder.append("</gpx>\n")
        return builder.toString()
    }

    private fun appendTrackPoint(builder: StringBuilder, entry: TickLogEntry) {
        val latitude = entry.latitude ?: return
        val longitude = entry.longitude ?: return

        builder.append("      <trkpt lat=\"")
            .append(formatCoordinate(latitude))
            .append("\" lon=\"")
            .append(formatCoordinate(longitude))
            .append("\">")

        val timestamp = entry.sampleTimestampMillis
        if (timestamp != null) {
            builder.append("<time>")
                .append(formatIso8601Utc(timestamp))
                .append("</time>")
        }

        builder.append("</trkpt>\n")
    }

    private fun TickLogEntry.isTrackable(): Boolean {
        val latitude = latitude ?: return false
        val longitude = longitude ?: return false
        if (latitude.isNaN() || longitude.isNaN()) {
            return false
        }
        if (latitude.isInfinite() || longitude.isInfinite()) {
            return false
        }
        if (latitude < MIN_LATITUDE || latitude > MAX_LATITUDE) {
            return false
        }
        if (longitude < MIN_LONGITUDE || longitude > MAX_LONGITUDE) {
            return false
        }
        return true
    }

    private fun formatCoordinate(value: Double): String {
        // Locale.US : séparateur décimal point, jamais virgule (GPX impose le point).
        return String.format(Locale.US, "%.7f", value)
    }

    private fun formatIso8601Utc(epochMillis: Long): String {
        val format = SimpleDateFormat(ISO_8601_PATTERN, Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        return format.format(Date(epochMillis))
    }

    private fun escapeXml(value: String): String {
        val builder = StringBuilder(value.length)
        for (character in value) {
            when (character) {
                '&' -> builder.append("&amp;")
                '<' -> builder.append("&lt;")
                '>' -> builder.append("&gt;")
                '"' -> builder.append("&quot;")
                '\'' -> builder.append("&apos;")
                else -> builder.append(character)
            }
        }
        return builder.toString()
    }

    private const val DEFAULT_TRACK_NAME = "Rallye Trip Meter"
    private const val ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private const val XML_PROLOG = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
    private const val GPX_OPEN =
        "<gpx version=\"1.1\" creator=\"rallye-trip-meter-android\" " +
            "xmlns=\"http://www.topografix.com/GPX/1/1\">"

    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
}

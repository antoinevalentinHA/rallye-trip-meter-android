package fr.arsenal.rallyetripmeter.domain.diag

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/*
 * ARSENAL RALLYE — Parseur GPX (relecture a posteriori)
 *
 * Rôle :
 * - Lit un document GPX (en priorité celui produit par GpxWriter) et en extrait
 *   une GpxTrack : points lat/lon, et time si présent.
 *
 * Contraintes :
 * - Domaine pur : aucune dépendance Android, aucune dépendance XML externe.
 *   Parseur tolérant à base de balayage de chaîne, suffisant pour le GPX simple
 *   que nous générons (et robuste aux espaces / retours à la ligne courants).
 * - Aucun calcul de distance, aucun lien au moteur.
 *
 * Principe :
 * - Extrait chaque élément <trkpt ... lat="..." lon="..."> ... </trkpt>.
 * - lat/lon lus depuis les attributs ; <time> lu dans le corps du trkpt si présent.
 * - Un point dont lat ou lon est absente, non numérique, NaN/infini ou hors bornes
 *   est IGNORÉ silencieusement.
 * - <name> du <trk> extrait si présent (dé-échappé).
 * - Aucune exception émise pour un contenu malformé : retourne une trace vide.
 */
object GpxTrackReader {

    fun parse(gpx: String): GpxTrack {
        val name = extractTrackName(gpx)
        val points = ArrayList<GpxTrackPoint>()

        var searchIndex = 0
        while (true) {
            val openStart = gpx.indexOf("<trkpt", searchIndex)
            if (openStart < 0) {
                break
            }
            val openEnd = gpx.indexOf('>', openStart)
            if (openEnd < 0) {
                break
            }

            val openingTag = gpx.substring(openStart, openEnd + 1)

            // Corps jusqu'au </trkpt> (ou auto-fermant : pas de corps).
            val selfClosing = openingTag.endsWith("/>")
            val bodyEnd: Int
            val body: String
            if (selfClosing) {
                body = ""
                bodyEnd = openEnd + 1
            } else {
                val close = gpx.indexOf("</trkpt>", openEnd)
                if (close < 0) {
                    break
                }
                body = gpx.substring(openEnd + 1, close)
                bodyEnd = close + "</trkpt>".length
            }
            searchIndex = bodyEnd

            val latitude = readAttribute(openingTag, "lat")?.toDoubleOrNull()
            val longitude = readAttribute(openingTag, "lon")?.toDoubleOrNull()
            if (!isValidCoordinate(latitude, longitude)) {
                continue
            }

            val timeMillis = extractTime(body)
            points.add(
                GpxTrackPoint(
                    latitude = latitude!!,
                    longitude = longitude!!,
                    timeMillis = timeMillis
                )
            )
        }

        return GpxTrack(name = name, points = points)
    }

    private fun isValidCoordinate(latitude: Double?, longitude: Double?): Boolean {
        if (latitude == null || longitude == null) {
            return false
        }
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

    private fun readAttribute(tag: String, attribute: String): String? {
        val key = "$attribute=\""
        val start = tag.indexOf(key)
        if (start < 0) {
            return null
        }
        val valueStart = start + key.length
        val valueEnd = tag.indexOf('"', valueStart)
        if (valueEnd < 0) {
            return null
        }
        return tag.substring(valueStart, valueEnd)
    }

    private fun extractTime(body: String): Long? {
        val open = body.indexOf("<time>")
        if (open < 0) {
            return null
        }
        val close = body.indexOf("</time>", open)
        if (close < 0) {
            return null
        }
        val raw = body.substring(open + "<time>".length, close).trim()
        if (raw.isEmpty()) {
            return null
        }
        return parseIso8601Utc(raw)
    }

    private fun parseIso8601Utc(value: String): Long? {
        return try {
            val format = SimpleDateFormat(ISO_8601_PATTERN, Locale.US)
            format.timeZone = TimeZone.getTimeZone("UTC")
            format.isLenient = false
            format.parse(value)?.time
        } catch (_: Exception) {
            null
        }
    }

    private fun extractTrackName(gpx: String): String? {
        // Premier <name> rencontré après <trk> (le writer place le name dans <trk>).
        val trkStart = gpx.indexOf("<trk>")
        val from = if (trkStart >= 0) trkStart else 0
        val open = gpx.indexOf("<name>", from)
        if (open < 0) {
            return null
        }
        val close = gpx.indexOf("</name>", open)
        if (close < 0) {
            return null
        }
        val raw = gpx.substring(open + "<name>".length, close)
        return unescapeXml(raw)
    }

    private fun unescapeXml(value: String): String {
        return value
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")
    }

    private const val ISO_8601_PATTERN = "yyyy-MM-dd'T'HH:mm:ss'Z'"

    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
}

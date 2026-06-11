package fr.arsenal.rallyetripmeter.domain.diag

import fr.arsenal.rallyetripmeter.domain.model.GpsStatus
import fr.arsenal.rallyetripmeter.domain.model.TripSessionState

/*
 * ARSENAL RALLYE — Tick log JSONL codec
 *
 * Rôle :
 * - Encode TickLogMeta et TickLogEntry en lignes JSONL (une ligne = un objet
 *   JSON plat) et les décode pour le replay JVM.
 *
 * Contraintes :
 * - Pur Kotlin/JVM : aucune dépendance JSON externe.
 * - Schéma plat versionné ("v") : aucune imbrication, clés fixes, types
 *   primitifs uniquement.
 * - Aucun lien avec l'UI, Android, ni le système de fichiers.
 * - Aucune perte au round-trip : parse(encode(x)) == x.
 *
 * Principe :
 * - Chaque ligne porte "v" (version de schéma) et "type" ("meta" ou "tick").
 * - Les champs absents sont émis comme null littéral (schéma stable, jamais
 *   de clé omise).
 * - Les valeurs Double non finies sont refusées à l'encodage (JSON strict).
 * - Toute ligne malformée, de version ou de type inattendu, est refusée
 *   au décodage par IllegalArgumentException.
 */
object TickLogJsonl {
    const val SCHEMA_VERSION: Int = 1

    private const val TYPE_META = "meta"
    private const val TYPE_TICK = "tick"

    // ------------------------------------------------------------------
    // Encodage
    // ------------------------------------------------------------------

    fun encodeMeta(meta: TickLogMeta): String {
        val builder = StringBuilder(INITIAL_LINE_CAPACITY)
        builder.append('{')
        builder.appendNumberField("v", SCHEMA_VERSION.toString())
        builder.append(',').appendStringField("type", TYPE_META)
        builder.append(',').appendStringField("commit", meta.commitHash)
        builder.append(',').appendStringField("device", meta.device)
        builder.append(',').appendNumberField(
            "started_at_ms",
            meta.startedAtMillis.toString()
        )
        builder.append('}')
        return builder.toString()
    }

    fun encodeEntry(entry: TickLogEntry): String {
        val builder = StringBuilder(INITIAL_LINE_CAPACITY)
        builder.append('{')
        builder.appendNumberField("v", SCHEMA_VERSION.toString())
        builder.append(',').appendStringField("type", TYPE_TICK)
        builder.append(',').appendNumberField(
            "tick_elapsed_ms",
            entry.tickElapsedMillis.toString()
        )
        builder.append(',').appendNullableNumberField(
            "sample_ts_ms",
            entry.sampleTimestampMillis?.toString()
        )
        builder.append(',').appendNullableBooleanField(
            "sample_is_new",
            entry.sampleIsNew
        )
        builder.append(',').appendNullableDoubleField("lat", entry.latitude)
        builder.append(',').appendNullableDoubleField("lon", entry.longitude)
        builder.append(',').appendNullableDoubleField(
            "accuracy_m",
            entry.accuracyMeters
        )
        builder.append(',').appendNullableDoubleField(
            "speed_mps",
            entry.speedMetersPerSecond
        )
        builder.append(',').appendStringField("gps_status", entry.gpsStatus.name)
        builder.append(',').appendStringField(
            "session_state",
            entry.sessionState.name
        )
        builder.append(',').appendNullableNumberField(
            "prev_ts_ms",
            entry.previousTimestampMillis?.toString()
        )
        builder.append(',').appendNullableDoubleField(
            "segment_m",
            entry.segmentMeters
        )
        builder.append(',').appendStringField("verdict", entry.verdict.name)
        builder.append(',').appendNullableDoubleField("floor_m", entry.floorMeters)
        builder.append(',').appendNullableDoubleField(
            "implied_speed_kmh",
            entry.impliedSpeedKmh
        )
        builder.append(',').appendDoubleField(
            "delta_total_m",
            entry.deltaTotalMeters
        )
        builder.append(',').appendDoubleField("total_m", entry.totalMeters)
        builder.append('}')
        return builder.toString()
    }

    // ------------------------------------------------------------------
    // Décodage
    // ------------------------------------------------------------------

    fun parseMeta(line: String): TickLogMeta {
        val fields = parseFlatObject(line)
        requireSchema(fields, TYPE_META)

        return TickLogMeta(
            commitHash = fields.requireString("commit"),
            device = fields.requireString("device"),
            startedAtMillis = fields.requireLong("started_at_ms")
        )
    }

    fun parseEntry(line: String): TickLogEntry {
        val fields = parseFlatObject(line)
        requireSchema(fields, TYPE_TICK)

        return TickLogEntry(
            tickElapsedMillis = fields.requireLong("tick_elapsed_ms"),
            sampleTimestampMillis = fields.optionalLong("sample_ts_ms"),
            sampleIsNew = fields.optionalBoolean("sample_is_new"),
            latitude = fields.optionalDouble("lat"),
            longitude = fields.optionalDouble("lon"),
            accuracyMeters = fields.optionalDouble("accuracy_m"),
            speedMetersPerSecond = fields.optionalDouble("speed_mps"),
            gpsStatus = fields.requireEnum("gps_status") { GpsStatus.valueOf(it) },
            sessionState = fields.requireEnum("session_state") {
                TripSessionState.valueOf(it)
            },
            previousTimestampMillis = fields.optionalLong("prev_ts_ms"),
            segmentMeters = fields.optionalDouble("segment_m"),
            verdict = fields.requireEnum("verdict") { SampleVerdict.valueOf(it) },
            floorMeters = fields.optionalDouble("floor_m"),
            impliedSpeedKmh = fields.optionalDouble("implied_speed_kmh"),
            deltaTotalMeters = fields.requireDouble("delta_total_m"),
            totalMeters = fields.requireDouble("total_m")
        )
    }

    // ------------------------------------------------------------------
    // Écriture des champs
    // ------------------------------------------------------------------

    private fun StringBuilder.appendKey(name: String): StringBuilder {
        append('"').append(name).append('"').append(':')
        return this
    }

    private fun StringBuilder.appendNumberField(
        name: String,
        rawNumber: String
    ): StringBuilder {
        appendKey(name).append(rawNumber)
        return this
    }

    private fun StringBuilder.appendNullableNumberField(
        name: String,
        rawNumber: String?
    ): StringBuilder {
        appendKey(name).append(rawNumber ?: NULL_LITERAL)
        return this
    }

    private fun StringBuilder.appendDoubleField(
        name: String,
        value: Double
    ): StringBuilder {
        require(value.isFinite()) {
            "Champ '$name' non fini : $value (JSON strict requis)."
        }
        appendKey(name).append(value.toString())
        return this
    }

    private fun StringBuilder.appendNullableDoubleField(
        name: String,
        value: Double?
    ): StringBuilder {
        if (value == null) {
            appendKey(name).append(NULL_LITERAL)
            return this
        }
        return appendDoubleField(name, value)
    }

    private fun StringBuilder.appendNullableBooleanField(
        name: String,
        value: Boolean?
    ): StringBuilder {
        appendKey(name).append(value?.toString() ?: NULL_LITERAL)
        return this
    }

    private fun StringBuilder.appendStringField(
        name: String,
        value: String
    ): StringBuilder {
        appendKey(name).append('"')
        for (character in value) {
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> {
                    if (character < ' ') {
                        append("\\u")
                        append(character.code.toString(16).padStart(4, '0'))
                    } else {
                        append(character)
                    }
                }
            }
        }
        append('"')
        return this
    }

    // ------------------------------------------------------------------
    // Lecture : analyseur d'objet JSON plat (chaîne, nombre, booléen, null)
    // ------------------------------------------------------------------

    private sealed interface RawValue {
        data class Text(val value: String) : RawValue
        data class Number(val raw: String) : RawValue
        data class Bool(val value: Boolean) : RawValue
        data object Null : RawValue
    }

    private fun parseFlatObject(line: String): Map<String, RawValue> {
        val cursor = Cursor(line)
        cursor.skipWhitespace()
        cursor.expect('{')

        val fields = LinkedHashMap<String, RawValue>()
        cursor.skipWhitespace()

        if (cursor.peek() == '}') {
            cursor.advance()
            cursor.requireEndOfLine()
            return fields
        }

        while (true) {
            cursor.skipWhitespace()
            val key = cursor.readString()
            cursor.skipWhitespace()
            cursor.expect(':')
            cursor.skipWhitespace()
            fields[key] = cursor.readValue()
            cursor.skipWhitespace()

            when (val separator = cursor.peek()) {
                ',' -> cursor.advance()
                '}' -> {
                    cursor.advance()
                    cursor.requireEndOfLine()
                    return fields
                }

                else -> throw IllegalArgumentException(
                    "Ligne JSONL malformée : ',' ou '}' attendu, " +
                        "trouvé '${separator ?: "fin de ligne"}'."
                )
            }
        }
    }

    private class Cursor(private val line: String) {
        private var index: Int = 0

        fun peek(): Char? = line.getOrNull(index)

        fun advance(): Char {
            val character = line.getOrNull(index)
                ?: throw IllegalArgumentException(
                    "Ligne JSONL malformée : fin de ligne inattendue."
                )
            index++
            return character
        }

        fun expect(expected: Char) {
            val character = advance()
            if (character != expected) {
                throw IllegalArgumentException(
                    "Ligne JSONL malformée : '$expected' attendu, trouvé '$character'."
                )
            }
        }

        fun skipWhitespace() {
            while (peek() == ' ') {
                index++
            }
        }

        fun requireEndOfLine() {
            skipWhitespace()
            if (index != line.length) {
                throw IllegalArgumentException(
                    "Ligne JSONL malformée : contenu inattendu après l'objet."
                )
            }
        }

        fun readString(): String {
            expect('"')
            val builder = StringBuilder()
            while (true) {
                when (val character = advance()) {
                    '"' -> return builder.toString()
                    '\\' -> builder.append(readEscape())
                    else -> builder.append(character)
                }
            }
        }

        private fun readEscape(): Char {
            return when (val escaped = advance()) {
                '"' -> '"'
                '\\' -> '\\'
                '/' -> '/'
                'n' -> '\n'
                'r' -> '\r'
                't' -> '\t'
                'b' -> '\b'
                'f' -> '\u000C'
                'u' -> {
                    val hex = StringBuilder(4)
                    repeat(4) { hex.append(advance()) }
                    val code = hex.toString().toIntOrNull(16)
                        ?: throw IllegalArgumentException(
                            "Ligne JSONL malformée : séquence \\u invalide '$hex'."
                        )
                    code.toChar()
                }

                else -> throw IllegalArgumentException(
                    "Ligne JSONL malformée : échappement inconnu '\\$escaped'."
                )
            }
        }

        fun readValue(): RawValue {
            return when (val character = peek()) {
                '"' -> RawValue.Text(readString())
                't' -> {
                    readLiteral("true")
                    RawValue.Bool(true)
                }

                'f' -> {
                    readLiteral("false")
                    RawValue.Bool(false)
                }

                'n' -> {
                    readLiteral("null")
                    RawValue.Null
                }

                else -> {
                    if (character != null && (character == '-' || character.isDigit())) {
                        RawValue.Number(readNumberToken())
                    } else {
                        throw IllegalArgumentException(
                            "Ligne JSONL malformée : valeur inattendue " +
                                "'${character ?: "fin de ligne"}'."
                        )
                    }
                }
            }
        }

        private fun readLiteral(literal: String) {
            for (expected in literal) {
                expect(expected)
            }
        }

        private fun readNumberToken(): String {
            val start = index
            while (true) {
                val character = peek() ?: break
                if (character.isDigit() ||
                    character == '-' || character == '+' ||
                    character == '.' || character == 'e' || character == 'E'
                ) {
                    index++
                } else {
                    break
                }
            }
            if (index == start) {
                throw IllegalArgumentException(
                    "Ligne JSONL malformée : nombre attendu."
                )
            }
            return line.substring(start, index)
        }
    }

    // ------------------------------------------------------------------
    // Extraction typée des champs
    // ------------------------------------------------------------------

    private fun requireSchema(
        fields: Map<String, RawValue>,
        expectedType: String
    ) {
        val version = fields.requireLong("v")
        if (version != SCHEMA_VERSION.toLong()) {
            throw IllegalArgumentException(
                "Version de schéma inattendue : $version (attendu $SCHEMA_VERSION)."
            )
        }

        val type = fields.requireString("type")
        if (type != expectedType) {
            throw IllegalArgumentException(
                "Type de ligne inattendu : '$type' (attendu '$expectedType')."
            )
        }
    }

    private fun Map<String, RawValue>.rawField(name: String): RawValue {
        return this[name]
            ?: throw IllegalArgumentException("Champ manquant : '$name'.")
    }

    private fun Map<String, RawValue>.requireString(name: String): String {
        val value = rawField(name)
        if (value !is RawValue.Text) {
            throw IllegalArgumentException("Champ '$name' : chaîne attendue.")
        }
        return value.value
    }

    private fun Map<String, RawValue>.requireLong(name: String): Long {
        val value = rawField(name)
        if (value !is RawValue.Number) {
            throw IllegalArgumentException("Champ '$name' : nombre attendu.")
        }
        return value.raw.toLongOrNull()
            ?: throw IllegalArgumentException(
                "Champ '$name' : entier invalide '${value.raw}'."
            )
    }

    private fun Map<String, RawValue>.optionalLong(name: String): Long? {
        if (rawField(name) is RawValue.Null) {
            return null
        }
        return requireLong(name)
    }

    private fun Map<String, RawValue>.requireDouble(name: String): Double {
        val value = rawField(name)
        if (value !is RawValue.Number) {
            throw IllegalArgumentException("Champ '$name' : nombre attendu.")
        }
        return value.raw.toDoubleOrNull()
            ?: throw IllegalArgumentException(
                "Champ '$name' : nombre invalide '${value.raw}'."
            )
    }

    private fun Map<String, RawValue>.optionalDouble(name: String): Double? {
        if (rawField(name) is RawValue.Null) {
            return null
        }
        return requireDouble(name)
    }

    private fun Map<String, RawValue>.optionalBoolean(name: String): Boolean? {
        return when (val value = rawField(name)) {
            is RawValue.Null -> null
            is RawValue.Bool -> value.value
            else -> throw IllegalArgumentException(
                "Champ '$name' : booléen attendu."
            )
        }
    }

    private inline fun <T> Map<String, RawValue>.requireEnum(
        name: String,
        valueOf: (String) -> T
    ): T {
        val raw = requireString(name)
        return try {
            valueOf(raw)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException(
                "Champ '$name' : valeur d'énumération inconnue '$raw'."
            )
        }
    }

    private const val NULL_LITERAL = "null"
    private const val INITIAL_LINE_CAPACITY = 320
}

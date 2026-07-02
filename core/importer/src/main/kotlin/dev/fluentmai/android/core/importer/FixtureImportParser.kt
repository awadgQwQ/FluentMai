package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
import org.json.JSONArray
import org.json.JSONObject

class FixtureImportParser {
    fun parse(json: String): List<ParsedScoreRecord> {
        val root = JSONObject(json)
        val records = root.optJSONArray("records") ?: JSONArray()
        return (0 until records.length()).map { index ->
            val record = records.getJSONObject(index)
            ParsedScoreRecord(
                title = record.nullableString("title"),
                songId = record.nullableInt("songId"),
                songType = SongType.fromWireName(record.nullableString("songType") ?: record.nullableString("type")),
                difficulty = record.difficulty(),
                level = record.nullableString("level"),
                levelIndex = record.nullableInt("levelIndex"),
                achievement = record.nullableDouble("achievement"),
                dxScore = record.nullableInt("dxScore"),
                fc = record.nullableString("fc"),
                fs = record.nullableString("fs"),
                rawFingerprint = Hashing.sha256(record.toString()),
            )
        }
    }

    private fun JSONObject.difficulty(): Difficulty? =
        when (val value = optional("difficulty")) {
            is Number -> Difficulty.fromLevelIndex(value.toInt())
            is String -> Difficulty.fromWireName(value)
            else -> null
        }

    private fun JSONObject.nullableString(name: String): String? =
        optional(name)?.toString()

    private fun JSONObject.nullableInt(name: String): Int? =
        when (val value = optional(name)) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        }

    private fun JSONObject.nullableDouble(name: String): Double? =
        when (val value = optional(name)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }

    private fun JSONObject.optional(name: String): Any? =
        if (has(name) && !isNull(name)) opt(name) else null
}

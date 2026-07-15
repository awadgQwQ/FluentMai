package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.ChartNotes
import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.MaimaiMajorVersion
import dev.fluentmai.android.core.model.SongType
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

class MaimaiSongCatalog private constructor(
    private val songsByNormalizedTitle: Map<String, SongMetadata>,
    private val charts: List<ChartRecord>,
    private val majorVersions: List<MaimaiMajorVersion>,
) {
    fun idForTitle(title: String): Int? =
        songsByNormalizedTitle[normalizeTitle(title)]?.id

    fun resolveSongType(
        title: String,
        levelIndex: Int,
        detectedType: SongType,
        detectedLevel: String? = null,
    ): SongType {
        val song = songsByNormalizedTitle[normalizeTitle(title)] ?: return detectedType
        val normalizedDetectedLevel = detectedLevel.normalizedLevel()
        if (normalizedDetectedLevel != null) {
            val levelMatchedTypes = SongType.entries.filter { type ->
                song.chart(type, levelIndex)?.level?.normalizedLevel() == normalizedDetectedLevel
            }
            if (levelMatchedTypes.size == 1) return levelMatchedTypes.single()
        }

        if (song.hasChart(detectedType, levelIndex)) return detectedType

        val otherType = detectedType.other()
        return if (song.hasChart(otherType, levelIndex)) otherType else detectedType
    }

    fun levelForTitle(
        title: String,
        levelIndex: Int,
        songType: SongType,
    ): String? =
        songsByNormalizedTitle[normalizeTitle(title)]
            ?.chart(songType, levelIndex)
            ?.level

    fun chartExists(
        title: String,
        levelIndex: Int,
        songType: SongType,
    ): Boolean? =
        songsByNormalizedTitle[normalizeTitle(title)]
            ?.hasChart(songType, levelIndex)

    fun charts(): List<ChartRecord> = charts

    fun majorVersions(): List<MaimaiMajorVersion> = majorVersions

    fun songCount(): Int = songsByNormalizedTitle.size

    companion object {
        val Empty = MaimaiSongCatalog(emptyMap(), emptyList(), emptyList())

        fun fromLxnsSongListJson(json: String): MaimaiSongCatalog {
            val root = JSONObject(json)
            val songs = root.optJSONArray("songs") ?: return Empty
            val majorVersions = parseMajorVersions(root.optJSONArray("versions"))
            val versionNames = majorVersions.associate { it.id to it.name }
            val parsedSongs = linkedMapOf<String, SongMetadata>()
            val parsedCharts = mutableListOf<ChartRecord>()
            for (index in 0 until songs.length()) {
                val song = songs.optJSONObject(index) ?: continue
                val id = song.optInt("id", -1)
                // A released song (ID 1422) intentionally uses an ideographic-space
                // title. Empty means missing; whitespace can be valid catalog data.
                val title = song.optString("title").takeIf { it.isNotEmpty() }
                if (id >= 0 && title != null) {
                    val artist = song.optString("artist")
                    val genre = song.optString("genre")
                    val bpm = song.optNullableInt("bpm")
                    val songVersion = song.optInt("version", 0)
                    val isLocked = song.optNullableBoolean("locked")
                    val isDisabled = song.optNullableBoolean("disabled")
                    val difficulties = song.optJSONObject("difficulties")
                    val standardCharts = parseCharts(
                        charts = difficulties?.optJSONArray("standard"),
                        versionNames = versionNames,
                    )
                    val dxCharts = parseCharts(
                        charts = difficulties?.optJSONArray("dx"),
                        versionNames = versionNames,
                    )
                    parsedSongs[normalizeTitle(title)] = SongMetadata(
                        id = id,
                        title = title,
                        artist = artist,
                        genre = genre,
                        bpm = bpm,
                        version = songVersion,
                        versionName = versionNames[songVersion],
                        chartsByType = mapOf(
                            SongType.STANDARD to standardCharts,
                            SongType.DX to dxCharts,
                        ),
                    )
                    parsedCharts += standardCharts.toChartRecords(
                        songId = id,
                        title = title,
                        artist = artist,
                        genre = genre,
                        bpm = bpm,
                        songVersion = songVersion,
                        songVersionName = versionNames[songVersion],
                        songType = SongType.STANDARD,
                        isLocked = isLocked,
                        isDisabled = isDisabled,
                    )
                    parsedCharts += dxCharts.toChartRecords(
                        songId = id,
                        title = title,
                        artist = artist,
                        genre = genre,
                        bpm = bpm,
                        songVersion = songVersion,
                        songVersionName = versionNames[songVersion],
                        songType = SongType.DX,
                        isLocked = isLocked,
                        isDisabled = isDisabled,
                    )
                }
            }
            return MaimaiSongCatalog(parsedSongs, parsedCharts, majorVersions)
        }

        private fun parseMajorVersions(versions: JSONArray?): List<MaimaiMajorVersion> {
            if (versions == null) return emptyList()
            val parsed = linkedMapOf<Int, MaimaiMajorVersion>()
            for (index in 0 until versions.length()) {
                val version = versions.optJSONObject(index) ?: continue
                val versionNumber = version.optInt("version", 0)
                val title = version.optString("title").takeIf { it.isNotBlank() }
                if (versionNumber > 0 && title != null) {
                    parsed[versionNumber] = MaimaiMajorVersion(versionNumber, title.trim())
                }
            }
            return parsed.values.sortedBy { it.id }
        }

        private fun parseCharts(
            charts: JSONArray?,
            versionNames: Map<Int, String>,
        ): Map<Int, ChartMetadata> {
            if (charts == null) return emptyMap()
            val parsed = linkedMapOf<Int, ChartMetadata>()
            for (index in 0 until charts.length()) {
                val chart = charts.optJSONObject(index) ?: continue
                val level = chart.optString("level").takeIf { it.isNotBlank() }
                val levelIndex = chart.optNullableInt("difficulty") ?: index
                val chartVersion = chart.optInt("version", 0)
                parsed[levelIndex] = ChartMetadata(
                    level = level,
                    levelValue = chart.optNullableDouble("level_value"),
                    noteDesigner = chart.optString("note_designer").takeIf { it.isNotBlank() }.orEmpty(),
                    version = chartVersion,
                    versionName = versionNames[chartVersion],
                    notes = chart.optJSONObject("notes")?.let { notes ->
                        ChartNotes(
                            total = notes.optNullableInt("total"),
                            tap = notes.optNullableInt("tap"),
                            hold = notes.optNullableInt("hold"),
                            slide = notes.optNullableInt("slide"),
                            touch = notes.optNullableInt("touch"),
                            breakCount = notes.optNullableInt("break"),
                        )
                    },
                )
            }
            return parsed
        }

        private fun normalizeTitle(title: String): String =
            Normalizer.normalize(title.trim(), Normalizer.Form.NFKC).lowercase()

        private fun JSONObject.optNullableInt(name: String): Int? =
            if (has(name) && !isNull(name)) optInt(name) else null

        private fun JSONObject.optNullableDouble(name: String): Double? =
            if (has(name) && !isNull(name)) optDouble(name) else null

        private fun JSONObject.optNullableBoolean(name: String): Boolean? =
            if (has(name) && !isNull(name)) optBoolean(name) else null
    }
}

private data class SongMetadata(
    val id: Int,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int?,
    val version: Int,
    val versionName: String?,
    val chartsByType: Map<SongType, Map<Int, ChartMetadata>>,
) {
    fun chart(songType: SongType, levelIndex: Int): ChartMetadata? =
        chartsByType[songType]?.get(levelIndex)

    fun hasChart(songType: SongType, levelIndex: Int): Boolean =
        chart(songType, levelIndex) != null
}

private data class ChartMetadata(
    val level: String?,
    val levelValue: Double?,
    val noteDesigner: String,
    val version: Int,
    val versionName: String?,
    val notes: ChartNotes?,
)

private fun SongType.other(): SongType =
    when (this) {
        SongType.STANDARD -> SongType.DX
        SongType.DX -> SongType.STANDARD
    }

private fun String?.normalizedLevel(): String? =
    this?.trim()?.uppercase()?.takeIf { it.isNotBlank() }

private fun Map<Int, ChartMetadata>.toChartRecords(
    songId: Int,
    title: String,
    artist: String,
    genre: String,
    bpm: Int?,
    songVersion: Int,
    songVersionName: String?,
    songType: SongType,
    isLocked: Boolean?,
    isDisabled: Boolean?,
): List<ChartRecord> =
    mapNotNull { (levelIndex, chart) ->
        val difficulty = Difficulty.fromLevelIndex(levelIndex) ?: return@mapNotNull null
        ChartRecord(
            songId = songId,
            title = title,
            artist = artist,
            genre = genre,
            bpm = bpm,
            songVersion = songVersion,
            songVersionName = songVersionName,
            chartVersion = chart.version,
            chartVersionName = chart.versionName,
            songType = songType,
            difficulty = difficulty,
            levelIndex = levelIndex,
            level = chart.level.orEmpty(),
            levelValue = chart.levelValue,
            noteDesigner = chart.noteDesigner,
            notes = chart.notes,
            isLocked = isLocked,
            isDisabled = isDisabled,
        )
    }

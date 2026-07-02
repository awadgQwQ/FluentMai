package dev.fluentmai.android.core.exporter

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MaimaiScoreExporterTest {
    private val exporter = MaimaiScoreExporter()

    @Test
    fun exportsDivingFishUpdateRecordsList() {
        val json = exporter.toDivingFishUpdateRecordsJson(
            listOf(score(songType = SongType.DX, fc = "FC", fs = "FS")),
        )

        val record = JSONArray(json).getJSONObject(0)
        assertEquals(100.5, record.getDouble("achievements"), 0.0001)
        assertEquals(3120, record.getInt("dxScore"))
        assertEquals("fc", record.getString("fc"))
        assertEquals("fs", record.getString("fs"))
        assertEquals(3, record.getInt("level_index"))
        assertEquals("PANDORA PARADOXXX", record.getString("title"))
        assertEquals("DX", record.getString("type"))
    }

    @Test
    fun exportsLxnsUserScoresWrapper() {
        val json = exporter.toLxnsUserScoresJson(
            listOf(score(songId = 834, songType = SongType.STANDARD, fc = "app", fs = null)),
        )

        val record = JSONObject(json).getJSONArray("scores").getJSONObject(0)
        assertEquals(834, record.getInt("id"))
        assertEquals("standard", record.getString("type"))
        assertEquals(3, record.getInt("level_index"))
        assertEquals(100.5, record.getDouble("achievements"), 0.0001)
        assertEquals("app", record.getString("fc"))
        assertEquals(true, record.isNull("fs"))
        assertEquals(3120, record.getInt("dx_score"))
    }

    @Test
    fun lxnsExportRequiresSongId() {
        val error = assertThrows(MaimaiScoreExportException::class.java) {
            exporter.toLxnsUserScoresJson(listOf(score(songId = null)))
        }

        assertEquals(true, error.message?.contains("requires songId"))
    }

    @Test
    fun divingFishExportDefaultsMissingDxScoreToZero() {
        val json = exporter.toDivingFishUpdateRecordsJson(listOf(score(dxScore = null)))

        val record = JSONArray(json).getJSONObject(0)
        assertEquals(0, record.getInt("dxScore"))
    }

    @Test
    fun lxnsExportRequiresDxScore() {
        val error = assertThrows(MaimaiScoreExportException::class.java) {
            exporter.toLxnsUserScoresJson(listOf(score(dxScore = null)))
        }

        assertEquals(true, error.message?.contains("requires dxScore"))
    }

    @Test
    fun divingFishTitleMappingBadAppleFeatDotToSpace() {
        val record = score(title = "Bad Apple!! feat.nomico", songType = SongType.STANDARD)
        val json = exporter.toDivingFishUpdateRecordsJson(listOf(record))
        val payload = JSONArray(json).getJSONObject(0)

        assertEquals("Bad Apple!! feat nomico", payload.getString("title"))
        assertEquals("SD", payload.getString("type"))
        assertEquals(3, payload.getInt("level_index"))
        assertEquals("Bad Apple!! feat.nomico", record.title)
    }

    @Test
    fun divingFishTitleMappingHelpMeErinnnnnnBandVer() {
        val record = score(title = "Help me, ERINNNNNN!!（Band ver.）", songType = SongType.STANDARD)
        val json = exporter.toDivingFishUpdateRecordsJson(listOf(record))
        val payload = JSONArray(json).getJSONObject(0)

        assertEquals("Help me, ERINNNNNN!!", payload.getString("title"))
        assertEquals("SD", payload.getString("type"))
        assertEquals(3, payload.getInt("level_index"))
        assertEquals("Help me, ERINNNNNN!!（Band ver.）", record.title)
    }

    @Test
    fun divingFishTitleUnmappedPassesThrough() {
        val record = score(title = "PANDORA PARADOXXX", songType = SongType.DX)
        val json = exporter.toDivingFishUpdateRecordsJson(listOf(record))
        val payload = JSONArray(json).getJSONObject(0)

        assertEquals("PANDORA PARADOXXX", payload.getString("title"))
        assertEquals("DX", payload.getString("type"))
    }

    @Test
    fun divingFishTitleMappingMultipleRecordsMixed() {
        val records = listOf(
            score(title = "Bad Apple!! feat.nomico", songType = SongType.STANDARD, levelIndex = 2),
            score(title = "Help me, ERINNNNNN!!（Band ver.）", songType = SongType.STANDARD, levelIndex = 2),
            score(title = "PANDORA PARADOXXX", songType = SongType.DX),
        )
        val json = exporter.toDivingFishUpdateRecordsJson(records)
        val array = JSONArray(json)

        assertEquals("Bad Apple!! feat nomico", array.getJSONObject(0).getString("title"))
        assertEquals("Help me, ERINNNNNN!!", array.getJSONObject(1).getString("title"))
        assertEquals("PANDORA PARADOXXX", array.getJSONObject(2).getString("title"))
    }

    private fun score(
        songId: Int? = 834,
        songType: SongType = SongType.STANDARD,
        title: String = "PANDORA PARADOXXX",
        levelIndex: Int = 3,
        dxScore: Int? = 3120,
        fc: String? = null,
        fs: String? = null,
    ): ScoreRecord =
        ScoreRecord(
            id = "score-1",
            songId = songId,
            title = title,
            songType = songType,
            difficulty = Difficulty.MASTER,
            level = "14+",
            levelIndex = levelIndex,
            achievement = 100.5,
            dxScore = dxScore,
            fc = fc,
            fs = fs,
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )
}

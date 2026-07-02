package dev.fluentmai.android.core.exporter

import dev.fluentmai.android.core.model.ScoreRecord
import org.json.JSONArray
import org.json.JSONObject

class MaimaiScoreExportException(message: String) : IllegalArgumentException(message)

class MaimaiScoreExporter {
    fun toDivingFishUpdateRecordsJson(scores: List<ScoreRecord>): String =
        JSONArray(scores.map(::divingFishRecord)).toString()

    fun toLxnsUserScoresJson(scores: List<ScoreRecord>): String =
        JSONObject()
            .put("scores", JSONArray(scores.map(::lxnsRecord)))
            .toString()

    private fun divingFishRecord(score: ScoreRecord): JSONObject =
        JSONObject()
            .put("achievements", score.achievement)
            .put("dxScore", score.dxScore ?: 0)
            .put("fc", score.fc.exportFlagOrEmpty())
            .put("fs", score.fs.exportFlagOrEmpty())
            .put("level_index", score.levelIndex)
            .put("title", mapToDivingFishTitle(score.title))
            .put("type", score.songType.divingFishName)

    private fun mapToDivingFishTitle(title: String): String =
        DIVING_FISH_TITLE_MAP[title] ?: title

    companion object {
        val DIVING_FISH_TITLE_MAP: Map<String, String> = mapOf(
            "Bad Apple!! feat.nomico" to "Bad Apple!! feat nomico",
            "Help me, ERINNNNNN!!（Band ver.）" to "Help me, ERINNNNNN!!",
        )
    }

    private fun lxnsRecord(score: ScoreRecord): JSONObject =
        JSONObject()
            .put("id", requireSongId(score))
            .put("type", score.songType.exportName)
            .put("level_index", score.levelIndex)
            .put("achievements", score.achievement)
            .put("fc", score.fc.exportFlagOrNull() ?: JSONObject.NULL)
            .put("fs", score.fs.exportFlagOrNull() ?: JSONObject.NULL)
            .put("dx_score", requireDxScore(score))

    private fun requireSongId(score: ScoreRecord): Int =
        score.songId ?: throw MaimaiScoreExportException(
            "LXNS export requires songId: scoreId=${score.id} title=${score.title}",
        )

    private fun requireDxScore(score: ScoreRecord): Int =
        score.dxScore ?: throw MaimaiScoreExportException(
            "Score export requires dxScore: scoreId=${score.id} title=${score.title}",
        )

    private fun String?.exportFlagOrEmpty(): String =
        exportFlagOrNull().orEmpty()

    private fun String?.exportFlagOrNull(): String? =
        this?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
}

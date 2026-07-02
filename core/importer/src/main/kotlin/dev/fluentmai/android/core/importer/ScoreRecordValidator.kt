package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType

data class ScoreRecordDraft(
    val id: String,
    val songId: Int?,
    val title: String,
    val songType: SongType,
    val difficulty: Difficulty,
    val level: String,
    val levelIndex: Int,
    val achievement: Double,
    val dxScore: Int?,
    val fc: String?,
    val fs: String?,
) {
    fun toScoreRecord(sourceBatchId: String, importedAt: Long): ScoreRecord =
        ScoreRecord(
            id = id,
            songId = songId,
            title = title,
            songType = songType,
            difficulty = difficulty,
            level = level,
            levelIndex = levelIndex,
            achievement = achievement,
            dxScore = dxScore,
            fc = fc,
            fs = fs,
            sourceBatchId = sourceBatchId,
            importedAt = importedAt,
        )
}

sealed interface ValidationOutcome {
    data class Valid(val draft: ScoreRecordDraft) : ValidationOutcome
    data class Invalid(
        val parsed: ParsedScoreRecord,
        val reasons: List<String>,
    ) : ValidationOutcome
}

class ScoreRecordValidator {
    fun validate(parsed: ParsedScoreRecord): ValidationOutcome {
        val reasons = mutableListOf<String>()
        val title = parsed.title?.trim().orEmpty()
        val level = parsed.level?.trim().orEmpty()
        val achievement = parsed.achievement
        val levelIndex = parsed.levelIndex
        val mappedDifficulty = levelIndex?.let(Difficulty::fromLevelIndex)
        val difficulty = parsed.difficulty ?: mappedDifficulty

        if (title.isBlank()) reasons += "blank_title"
        if (level.isBlank()) reasons += "blank_level"
        if (achievement == null || achievement < 0.0 || achievement > 101.0 || !achievement.isFinite()) {
            reasons += "invalid_achievement"
        }
        if (levelIndex == null || levelIndex !in 0..4) {
            reasons += "invalid_level_index"
        }
        if (difficulty == null) {
            reasons += "invalid_difficulty"
        }
        if (
            parsed.difficulty != null &&
            levelIndex != null &&
            levelIndex in 0..4 &&
            parsed.difficulty.levelIndex != levelIndex
        ) {
            reasons += "difficulty_level_index_mismatch"
        }

        if (reasons.isNotEmpty()) {
            return ValidationOutcome.Invalid(parsed = parsed, reasons = reasons.distinct())
        }

        return ValidationOutcome.Valid(
            ScoreRecordDraft(
                id = ScoreRecordIds.idFor(
                    title = title,
                    levelIndex = levelIndex!!,
                    songType = parsed.songType,
                ),
                songId = parsed.songId,
                title = title,
                songType = parsed.songType,
                difficulty = difficulty!!,
                level = level,
                levelIndex = levelIndex,
                achievement = achievement!!,
                dxScore = parsed.dxScore,
                fc = parsed.fc?.trim()?.takeIf { it.isNotBlank() },
                fs = parsed.fs?.trim()?.takeIf { it.isNotBlank() },
            ),
        )
    }
}

object ScoreRecordIds {
    fun idFor(
        title: String,
        levelIndex: Int,
        songType: SongType = SongType.STANDARD,
    ): String =
        "score-" + Hashing.sha256("score|${title.trim().lowercase()}|${songType.exportName}|$levelIndex")
}

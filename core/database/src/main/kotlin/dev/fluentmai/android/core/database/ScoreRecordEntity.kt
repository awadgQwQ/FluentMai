package dev.fluentmai.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord

@Entity(
    tableName = "score_records",
    indices = [
        Index(value = ["title", "levelIndex"], unique = true),
    ],
)
data class ScoreRecordEntity(
    @PrimaryKey val id: String,
    val title: String,
    val difficulty: String,
    val level: String,
    val levelIndex: Int,
    val achievement: Double,
    val dxScore: Int?,
    val fc: String?,
    val fs: String?,
    val sourceBatchId: String,
    val importedAt: Long,
)

fun ScoreRecord.toEntity(): ScoreRecordEntity =
    ScoreRecordEntity(
        id = id,
        title = title,
        difficulty = difficulty.name,
        level = level,
        levelIndex = levelIndex,
        achievement = achievement,
        dxScore = dxScore,
        fc = fc,
        fs = fs,
        sourceBatchId = sourceBatchId,
        importedAt = importedAt,
    )

fun ScoreRecordEntity.toModel(): ScoreRecord =
    ScoreRecord(
        id = id,
        title = title,
        difficulty = Difficulty.valueOf(difficulty),
        level = level,
        levelIndex = levelIndex,
        achievement = achievement,
        dxScore = dxScore,
        fc = fc,
        fs = fs,
        sourceBatchId = sourceBatchId,
        importedAt = importedAt,
    )


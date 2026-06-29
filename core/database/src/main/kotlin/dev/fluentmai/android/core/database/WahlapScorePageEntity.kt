package dev.fluentmai.android.core.database

import androidx.room.Entity
import dev.fluentmai.android.core.model.Difficulty

@Entity(
    tableName = "wahlap_score_pages",
    primaryKeys = ["sourceBatchId", "difficulty"],
)
data class WahlapScorePageEntity(
    val sourceBatchId: String,
    val difficulty: String,
    val levelIndex: Int,
    val html: String,
    val fetchedAt: Long,
)

data class CachedWahlapScorePage(
    val sourceBatchId: String,
    val difficulty: Difficulty,
    val levelIndex: Int = difficulty.levelIndex,
    val html: String,
    val fetchedAt: Long,
)

fun CachedWahlapScorePage.toEntity(sourceBatchId: String = this.sourceBatchId): WahlapScorePageEntity =
    WahlapScorePageEntity(
        sourceBatchId = sourceBatchId,
        difficulty = difficulty.name,
        levelIndex = levelIndex,
        html = html,
        fetchedAt = fetchedAt,
    )

fun WahlapScorePageEntity.toModel(): CachedWahlapScorePage =
    CachedWahlapScorePage(
        sourceBatchId = sourceBatchId,
        difficulty = Difficulty.valueOf(difficulty),
        levelIndex = levelIndex,
        html = html,
        fetchedAt = fetchedAt,
    )

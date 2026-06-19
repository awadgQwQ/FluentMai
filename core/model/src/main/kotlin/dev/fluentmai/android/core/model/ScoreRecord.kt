package dev.fluentmai.android.core.model

data class ScoreRecord(
    val id: String,
    val title: String,
    val difficulty: Difficulty,
    val level: String,
    val levelIndex: Int,
    val achievement: Double,
    val dxScore: Int?,
    val fc: String?,
    val fs: String?,
    val sourceBatchId: String,
    val importedAt: Long,
)


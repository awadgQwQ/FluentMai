package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType

data class ParsedScoreRecord(
    val title: String?,
    val songId: Int? = null,
    val songType: SongType = SongType.STANDARD,
    val difficulty: Difficulty?,
    val level: String?,
    val levelIndex: Int?,
    val achievement: Double?,
    val dxScore: Int?,
    val fc: String?,
    val fs: String?,
    val rawFingerprint: String,
)

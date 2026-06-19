package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty

data class ParsedScoreRecord(
    val title: String?,
    val difficulty: Difficulty?,
    val level: String?,
    val levelIndex: Int?,
    val achievement: Double?,
    val dxScore: Int?,
    val fc: String?,
    val fs: String?,
    val rawFingerprint: String,
)


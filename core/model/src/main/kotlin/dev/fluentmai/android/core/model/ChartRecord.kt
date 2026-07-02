package dev.fluentmai.android.core.model

data class ChartRecord(
    val songId: Int,
    val title: String,
    val artist: String,
    val genre: String,
    val bpm: Int?,
    val songVersion: Int,
    val songVersionName: String?,
    val chartVersion: Int,
    val chartVersionName: String?,
    val songType: SongType,
    val difficulty: Difficulty,
    val levelIndex: Int,
    val level: String,
    val levelValue: Double?,
    val noteDesigner: String,
    val notes: ChartNotes?,
)

data class ChartNotes(
    val total: Int?,
    val tap: Int?,
    val hold: Int?,
    val slide: Int?,
    val touch: Int?,
    val breakCount: Int?,
)

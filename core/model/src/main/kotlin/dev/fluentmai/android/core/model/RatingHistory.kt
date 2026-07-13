package dev.fluentmai.android.core.model

enum class RatingHistorySource(val displayName: String) {
    AUTOMATIC_IMPORT("自动导入"),
    MANUAL("手动补录"),
    UNKNOWN("未知来源"),
}

data class RatingHistoryEntry(
    val id: String,
    val recordedAtEpochMillis: Long,
    val rating: Int,
    val source: RatingHistorySource,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    val isManual: Boolean
        get() = source == RatingHistorySource.MANUAL
}

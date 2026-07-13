package dev.fluentmai.android.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import dev.fluentmai.android.core.model.RatingHistoryEntry
import dev.fluentmai.android.core.model.RatingHistorySource

@Entity(
    tableName = "rating_history",
    indices = [
        Index(value = ["recordedAtEpochMillis"]),
        Index(value = ["source", "recordedAtEpochMillis"]),
    ],
)
data class RatingHistoryEntity(
    @PrimaryKey val id: String,
    val recordedAtEpochMillis: Long,
    val rating: Int,
    val source: String,
    val note: String?,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
) {
    fun toModel(): RatingHistoryEntry =
        RatingHistoryEntry(
            id = id,
            recordedAtEpochMillis = recordedAtEpochMillis,
            rating = rating,
            source = RatingHistorySource.entries.firstOrNull { it.name == source }
                ?: RatingHistorySource.UNKNOWN,
            note = note,
            createdAtEpochMillis = createdAtEpochMillis,
            updatedAtEpochMillis = updatedAtEpochMillis,
        )
}

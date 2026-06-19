package dev.fluentmai.android.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.QuarantineRecord

@Entity(tableName = "quarantine_records")
data class QuarantineRecordEntity(
    @PrimaryKey val id: String,
    val reason: String,
    val difficulty: String?,
    val rawFingerprint: String,
    val sourceBatchId: String,
    val createdAt: Long,
)

fun QuarantineRecord.toEntity(): QuarantineRecordEntity =
    QuarantineRecordEntity(
        id = id,
        reason = reason,
        difficulty = difficulty?.name,
        rawFingerprint = rawFingerprint,
        sourceBatchId = sourceBatchId,
        createdAt = createdAt,
    )

fun QuarantineRecordEntity.toModel(): QuarantineRecord =
    QuarantineRecord(
        id = id,
        reason = reason,
        difficulty = difficulty?.let(Difficulty::valueOf),
        rawFingerprint = rawFingerprint,
        sourceBatchId = sourceBatchId,
        createdAt = createdAt,
    )


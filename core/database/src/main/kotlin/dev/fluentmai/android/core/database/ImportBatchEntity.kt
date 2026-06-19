package dev.fluentmai.android.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import dev.fluentmai.android.core.model.ImportBatch

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey val id: String,
    val source: String,
    val importedAt: Long,
    val totalParsed: Int,
    val inserted: Int,
    val updated: Int,
    val skippedDuplicate: Int,
    val quarantined: Int,
    val rejected: Int,
)

fun ImportBatch.toEntity(): ImportBatchEntity =
    ImportBatchEntity(
        id = id,
        source = source,
        importedAt = importedAt,
        totalParsed = totalParsed,
        inserted = inserted,
        updated = updated,
        skippedDuplicate = skippedDuplicate,
        quarantined = quarantined,
        rejected = rejected,
    )

fun ImportBatchEntity.toModel(): ImportBatch =
    ImportBatch(
        id = id,
        source = source,
        importedAt = importedAt,
        totalParsed = totalParsed,
        inserted = inserted,
        updated = updated,
        skippedDuplicate = skippedDuplicate,
        quarantined = quarantined,
        rejected = rejected,
    )


package dev.fluentmai.android.core.model

data class ImportBatch(
    val id: String,
    val source: String,
    val importedAt: Long,
    val totalParsed: Int,
    val inserted: Int,
    val updated: Int,
    val skippedDuplicate: Int,
    val quarantined: Int,
    val rejected: Int,
)


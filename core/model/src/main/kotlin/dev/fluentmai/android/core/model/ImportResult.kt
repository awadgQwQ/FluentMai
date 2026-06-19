package dev.fluentmai.android.core.model

data class ImportResult(
    val batchId: String,
    val inserted: Int,
    val updated: Int,
    val skippedDuplicate: Int,
    val quarantined: Int,
    val rejected: Int,
)


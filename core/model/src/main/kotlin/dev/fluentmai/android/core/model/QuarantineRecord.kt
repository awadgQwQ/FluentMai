package dev.fluentmai.android.core.model

data class QuarantineRecord(
    val id: String,
    val reason: String,
    val difficulty: Difficulty?,
    val rawFingerprint: String,
    val sourceBatchId: String,
    val createdAt: Long,
)


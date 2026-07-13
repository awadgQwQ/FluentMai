package dev.fluentmai.android.core.database

import androidx.room.Entity

/**
 * Legacy schema-only entity retained so existing version-5 databases remain compatible.
 * Runtime DAO access is intentionally absent: raw Wahlap HTML must not be persisted or read.
 */
@Entity(
    tableName = "wahlap_score_pages",
    primaryKeys = ["sourceBatchId", "difficulty"],
)
data class WahlapScorePageEntity(
    val sourceBatchId: String,
    val difficulty: String,
    val levelIndex: Int,
    val html: String,
    val fetchedAt: Long,
)

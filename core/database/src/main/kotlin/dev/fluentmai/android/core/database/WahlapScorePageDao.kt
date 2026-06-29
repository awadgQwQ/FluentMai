package dev.fluentmai.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface WahlapScorePageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<WahlapScorePageEntity>)

    @Query("DELETE FROM wahlap_score_pages")
    suspend fun deleteAll()

    @Query("SELECT * FROM wahlap_score_pages WHERE sourceBatchId = :batchId ORDER BY levelIndex ASC")
    suspend fun forBatch(batchId: String): List<WahlapScorePageEntity>
}

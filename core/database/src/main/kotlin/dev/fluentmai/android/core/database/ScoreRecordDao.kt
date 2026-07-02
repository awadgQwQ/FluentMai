package dev.fluentmai.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ScoreRecordDao {
    @Query("SELECT id FROM score_records WHERE id IN (:ids)")
    suspend fun findIds(ids: List<String>): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<ScoreRecordEntity>)

    @Query("DELETE FROM score_records WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>): Int

    @Query("SELECT COUNT(*) FROM score_records")
    suspend fun count(): Int

    @Query("SELECT * FROM score_records ORDER BY title COLLATE NOCASE ASC, levelIndex ASC")
    suspend fun getAll(): List<ScoreRecordEntity>
}

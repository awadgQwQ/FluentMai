package dev.fluentmai.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import dev.fluentmai.android.core.model.RatingHistorySource

@Dao
interface RatingHistoryDao {
    @Query("SELECT * FROM rating_history ORDER BY recordedAtEpochMillis ASC, createdAtEpochMillis ASC, id ASC")
    suspend fun getAll(): List<RatingHistoryEntity>

    @Query("SELECT * FROM rating_history ORDER BY recordedAtEpochMillis DESC, createdAtEpochMillis DESC, id DESC LIMIT 1")
    suspend fun latest(): RatingHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: RatingHistoryEntity)

    @Query(
        """
        UPDATE rating_history
        SET recordedAtEpochMillis = :recordedAtEpochMillis,
            rating = :rating,
            note = :note,
            updatedAtEpochMillis = :updatedAtEpochMillis
        WHERE id = :id AND source = 'MANUAL'
        """,
    )
    suspend fun updateManual(
        id: String,
        recordedAtEpochMillis: Long,
        rating: Int,
        note: String?,
        updatedAtEpochMillis: Long,
    ): Int

    @Query("DELETE FROM rating_history WHERE id = :id AND source = 'MANUAL'")
    suspend fun deleteManual(id: String): Int

    @Transaction
    suspend fun insertAutomaticIfChanged(entry: RatingHistoryEntity): Boolean {
        require(entry.source == RatingHistorySource.AUTOMATIC_IMPORT.name)
        if (latest()?.rating == entry.rating) return false
        insert(entry)
        return true
    }
}

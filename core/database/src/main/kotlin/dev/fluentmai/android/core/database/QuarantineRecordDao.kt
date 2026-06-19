package dev.fluentmai.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface QuarantineRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(records: List<QuarantineRecordEntity>)

    @Query("SELECT COUNT(*) FROM quarantine_records")
    suspend fun count(): Int

    @Query("SELECT * FROM quarantine_records ORDER BY createdAt DESC")
    suspend fun getAll(): List<QuarantineRecordEntity>
}


package dev.fluentmai.android.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ImportBatchDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: ImportBatchEntity)

    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC LIMIT 1")
    suspend fun latest(): ImportBatchEntity?
}


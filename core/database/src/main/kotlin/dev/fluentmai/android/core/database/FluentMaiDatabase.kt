package dev.fluentmai.android.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ScoreRecordEntity::class,
        ImportBatchEntity::class,
        QuarantineRecordEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class FluentMaiDatabase : RoomDatabase() {
    abstract fun scoreRecordDao(): ScoreRecordDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun quarantineRecordDao(): QuarantineRecordDao

    companion object {
        fun create(context: Context): FluentMaiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FluentMaiDatabase::class.java,
                "fluentmai-phase0.db",
            ).build()
    }
}


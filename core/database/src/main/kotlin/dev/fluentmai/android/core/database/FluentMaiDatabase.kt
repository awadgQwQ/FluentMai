package dev.fluentmai.android.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        ScoreRecordEntity::class,
        ImportBatchEntity::class,
        QuarantineRecordEntity::class,
        WahlapScorePageEntity::class,
        RatingHistoryEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class FluentMaiDatabase : RoomDatabase() {
    abstract fun scoreRecordDao(): ScoreRecordDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun quarantineRecordDao(): QuarantineRecordDao
    abstract fun ratingHistoryDao(): RatingHistoryDao

    companion object {
        fun create(context: Context): FluentMaiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FluentMaiDatabase::class.java,
                "fluentmai-phase0.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .build()

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    DELETE FROM score_records
                    WHERE sourceBatchId IN (
                        SELECT id FROM import_batches WHERE source LIKE 'asset:%'
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    DELETE FROM quarantine_records
                    WHERE sourceBatchId IN (
                        SELECT id FROM import_batches WHERE source LIKE 'asset:%'
                    )
                    """.trimIndent(),
                )
                db.execSQL("DELETE FROM import_batches WHERE source LIKE 'asset:%'")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wahlap_score_pages (
                        sourceBatchId TEXT NOT NULL,
                        difficulty TEXT NOT NULL,
                        levelIndex INTEGER NOT NULL,
                        html TEXT NOT NULL,
                        fetchedAt INTEGER NOT NULL,
                        PRIMARY KEY(sourceBatchId, difficulty)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DELETE FROM wahlap_score_pages")
            }
        }

        internal val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS rating_history (
                        id TEXT NOT NULL,
                        recordedAtEpochMillis INTEGER NOT NULL,
                        rating INTEGER NOT NULL,
                        source TEXT NOT NULL,
                        note TEXT,
                        createdAtEpochMillis INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rating_history_recordedAtEpochMillis " +
                        "ON rating_history(recordedAtEpochMillis)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_rating_history_source_recordedAtEpochMillis " +
                        "ON rating_history(source, recordedAtEpochMillis)",
                )
            }
        }
    }
}

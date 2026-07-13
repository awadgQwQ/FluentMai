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
    ],
    version = 5,
    exportSchema = false,
)
abstract class FluentMaiDatabase : RoomDatabase() {
    abstract fun scoreRecordDao(): ScoreRecordDao
    abstract fun importBatchDao(): ImportBatchDao
    abstract fun quarantineRecordDao(): QuarantineRecordDao
    abstract fun wahlapScorePageDao(): WahlapScorePageDao

    companion object {
        fun create(context: Context): FluentMaiDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                FluentMaiDatabase::class.java,
                "fluentmai-phase0.db",
            )
                .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .fallbackToDestructiveMigration()
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
    }
}

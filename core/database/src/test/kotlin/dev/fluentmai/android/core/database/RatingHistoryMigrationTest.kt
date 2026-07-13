package dev.fluentmai.android.core.database

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RatingHistoryMigrationTest {
    @Test
    fun migrationFromFiveCreatesEmptyHistoryWithoutChangingOldRows() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val database = Room.inMemoryDatabaseBuilder(context, FluentMaiDatabase::class.java).build()
        try {
            val writable = database.openHelper.writableDatabase
            writable.execSQL(
                """
                INSERT INTO import_batches(
                    id, source, importedAt, totalParsed, inserted, updated,
                    skippedDuplicate, quarantined, rejected
                ) VALUES ('migration-batch', 'test', 1, 1, 1, 0, 0, 0, 0)
                """.trimIndent(),
            )
            writable.execSQL("DROP TABLE rating_history")
            writable.version = 5
            FluentMaiDatabase.MIGRATION_5_6.migrate(writable)
            writable.version = 6
            val migratedDb: SupportSQLiteDatabase = database.openHelper.writableDatabase
            val oldRowCount = migratedDb.query("SELECT COUNT(*) FROM import_batches").use { cursor ->
                assertTrue(cursor.moveToFirst())
                cursor.getInt(0)
            }

            assertEquals(1, oldRowCount)
            assertTrue(database.ratingHistoryDao().getAll().isEmpty())
            assertEquals(6, migratedDb.version)
        } finally {
            database.close()
        }
    }
}

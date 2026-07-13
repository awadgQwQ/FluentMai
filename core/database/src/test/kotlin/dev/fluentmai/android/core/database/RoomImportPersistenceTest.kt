package dev.fluentmai.android.core.database

import androidx.room.Room
import dev.fluentmai.android.core.importer.MaimaiSongCatalog
import dev.fluentmai.android.core.importer.ImportPersistence
import dev.fluentmai.android.core.importer.ScoreRecordIds
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class RoomImportPersistenceTest {
    private lateinit var database: FluentMaiDatabase
    private lateinit var persistence: ImportPersistence

    @Before
    fun setup() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(),
            FluentMaiDatabase::class.java,
        ).build()
        persistence = RoomImportPersistence(database)
    }

    @After
    fun teardown() {
        database.close()
    }

    @Test
    fun validScoreInsertAppearsInDatabase() = runTest {
        val score = ScoreRecord(
            id = ScoreRecordIds.idFor("Test Song", 2),
            title = "Test Song",
            difficulty = Difficulty.EXPERT,
            level = "12+",
            levelIndex = 2,
            achievement = 99.5,
            dxScore = 2500,
            fc = "FC",
            fs = null,
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )

        persistence.insertScoreRecords(listOf(score))
        val count = database.scoreRecordDao().count()

        assertEquals(1, count)
    }

    @Test
    fun duplicateScoreReplacesExistingRecord() = runTest {
        val score = ScoreRecord(
            id = ScoreRecordIds.idFor("Dupe Song", 3),
            title = "Dupe Song",
            difficulty = Difficulty.MASTER,
            level = "14",
            levelIndex = 3,
            achievement = 100.0,
            dxScore = 3000,
            fc = "FC+",
            fs = "FS",
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )
        val updatedScore = score.copy(
            achievement = 100.5,
            dxScore = 3010,
            sourceBatchId = "batch-2",
            importedAt = 5678L,
        )

        persistence.insertScoreRecords(listOf(score))
        persistence.insertScoreRecords(listOf(updatedScore))
        val records = database.scoreRecordDao().getAll()

        assertEquals(1, records.size)
        assertEquals(100.5, records.single().achievement, 0.0001)
        assertEquals(3010, records.single().dxScore)
        assertEquals("batch-2", records.single().sourceBatchId)
    }

    @Test
    fun findExistingScoreIdsReturnsPreviouslyInserted() = runTest {
        val id = ScoreRecordIds.idFor("Known Song", 1)
        val score = ScoreRecord(
            id = id,
            title = "Known Song",
            difficulty = Difficulty.ADVANCED,
            level = "8",
            levelIndex = 1,
            achievement = 95.0,
            dxScore = 1800,
            fc = null,
            fs = null,
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )

        persistence.insertScoreRecords(listOf(score))
        val existing = persistence.findExistingScoreIds(setOf(id, "never-seen"))

        assertEquals(setOf(id), existing)
    }

    @Test
    fun quarantineRecordsAreWritten() = runTest {
        val quarantine = QuarantineRecord(
            id = "q-1",
            reason = "blank_title|invalid_achievement",
            difficulty = Difficulty.EXPERT,
            rawFingerprint = "abcdef1234567890",
            sourceBatchId = "batch-1",
            createdAt = 1234L,
        )

        persistence.insertQuarantineRecords(listOf(quarantine))
        val count = database.quarantineRecordDao().count()

        assertEquals(1, count)
        val all = database.quarantineRecordDao().getAll()
        assertEquals(1, all.size)
        assertEquals("q-1", all[0].id)
        assertEquals(Difficulty.EXPERT, all[0].toModel().difficulty)
    }

    @Test
    fun importBatchSummaryPersists() = runTest {
        val batch = ImportBatch(
            id = "batch-1",
            source = "test-fixture",
            importedAt = 1234L,
            totalParsed = 10,
            inserted = 8,
            updated = 0,
            skippedDuplicate = 1,
            quarantined = 1,
            rejected = 0,
        )

        persistence.insertImportBatch(batch)
        val latest = database.importBatchDao().latest()

        assertEquals("batch-1", latest?.id)
        assertEquals(10, latest?.totalParsed)
        assertEquals(8, latest?.inserted)
        assertEquals(1, latest?.skippedDuplicate)
        assertEquals(1, latest?.quarantined)
    }

    @Test
    fun blankTitleScoreNotWrittenToScoreTable() = runTest {
        val validScore = ScoreRecord(
            id = ScoreRecordIds.idFor("Valid", 2),
            title = "Valid",
            difficulty = Difficulty.EXPERT,
            level = "12",
            levelIndex = 2,
            achievement = 98.0,
            dxScore = 2400,
            fc = null,
            fs = null,
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )

        persistence.insertScoreRecords(listOf(validScore))

        val emptyTitleId = ScoreRecordIds.idFor("", 3)
        val existingIds = persistence.findExistingScoreIds(setOf(emptyTitleId))
        assertTrue(existingIds.isEmpty())
        assertEquals(1, database.scoreRecordDao().count())
    }

    @Test
    fun repositoryDeletesScoresWhoseChartTypeDoesNotExistInCatalog() = runTest {
        val valid = ScoreRecord(
            id = ScoreRecordIds.idFor("DX Only", 4, SongType.DX),
            title = "DX Only",
            songType = SongType.DX,
            difficulty = Difficulty.RE_MASTER,
            level = "13",
            levelIndex = 4,
            achievement = 100.6215,
            dxScore = 2275,
            fc = null,
            fs = "sync",
            sourceBatchId = "batch-1",
            importedAt = 1234L,
        )
        val invalid = valid.copy(
            id = ScoreRecordIds.idFor("DX Only", 4, SongType.STANDARD),
            songType = SongType.STANDARD,
        )
        val catalog = MaimaiSongCatalog.fromLxnsSongListJson(
            """
            {
              "songs": [
                {
                  "id": 1835,
                  "title": "DX Only",
                  "difficulties": {
                    "standard": [],
                    "dx": [
                      {"level": "3"},
                      {"level": "6"},
                      {"level": "9+"},
                      {"level": "11+"},
                      {"level": "13"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        persistence.insertScoreRecords(listOf(valid, invalid))
        val deleted = FluentMaiRepository(database).deleteScoresNotInCatalog(catalog)

        assertEquals(1, deleted)
        assertEquals(listOf(SongType.DX.name), database.scoreRecordDao().getAll().map { it.songType })
    }

    @Test
    fun repositoryOperationsPreserveLegacyWahlapPageRows() = runTest {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            """
            INSERT INTO wahlap_score_pages(sourceBatchId, difficulty, levelIndex, html, fetchedAt)
            VALUES ('legacy-batch', 'EXPERT', 2, 'legacy-private-page', 1000)
            """.trimIndent(),
        )

        FluentMaiRepository(database).scoreCount()

        val cursor = sqlite.query("SELECT COUNT(*) FROM wahlap_score_pages")
        cursor.use {
            assertTrue(it.moveToFirst())
            assertEquals(1, it.getInt(0))
        }
    }
}

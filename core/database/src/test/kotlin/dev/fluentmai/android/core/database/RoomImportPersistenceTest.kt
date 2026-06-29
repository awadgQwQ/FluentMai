package dev.fluentmai.android.core.database

import androidx.room.Room
import dev.fluentmai.android.core.importer.ImportPersistence
import dev.fluentmai.android.core.importer.ScoreRecordIds
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
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
    fun duplicateScoreIsIgnored() = runTest {
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

        persistence.insertScoreRecords(listOf(score))
        persistence.insertScoreRecords(listOf(score))
        val count = database.scoreRecordDao().count()

        assertEquals(1, count)
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
    fun repositoryStoresOnlyLatestWahlapScorePages() = runTest {
        val repository = FluentMaiRepository(database)
        persistence.insertImportBatch(
            ImportBatch(
                id = "batch-old",
                source = "wahlap:real-device",
                importedAt = 1000L,
                totalParsed = 1,
                inserted = 1,
                updated = 0,
                skippedDuplicate = 0,
                quarantined = 0,
                rejected = 0,
            ),
        )
        repository.replaceLatestWahlapScorePages(
            batchId = "batch-old",
            pages = listOf(
                CachedWahlapScorePage(
                    sourceBatchId = "",
                    difficulty = Difficulty.EXPERT,
                    html = "old-html",
                    fetchedAt = 1001L,
                ),
            ),
        )
        persistence.insertImportBatch(
            ImportBatch(
                id = "batch-new",
                source = "wahlap:real-device",
                importedAt = 2000L,
                totalParsed = 2,
                inserted = 2,
                updated = 0,
                skippedDuplicate = 0,
                quarantined = 0,
                rejected = 0,
            ),
        )
        repository.replaceLatestWahlapScorePages(
            batchId = "batch-new",
            pages = listOf(
                CachedWahlapScorePage(
                    sourceBatchId = "",
                    difficulty = Difficulty.BASIC,
                    html = "basic-html",
                    fetchedAt = 2001L,
                ),
                CachedWahlapScorePage(
                    sourceBatchId = "",
                    difficulty = Difficulty.MASTER,
                    html = "master-html",
                    fetchedAt = 2002L,
                ),
            ),
        )

        val pages = repository.latestWahlapScorePages()

        assertEquals(listOf(Difficulty.BASIC, Difficulty.MASTER), pages.map { it.difficulty })
        assertEquals(listOf("basic-html", "master-html"), pages.map { it.html })
        assertEquals(true, pages.all { it.sourceBatchId == "batch-new" })
    }
}

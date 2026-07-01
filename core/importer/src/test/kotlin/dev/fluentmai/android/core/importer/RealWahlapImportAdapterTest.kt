package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RealWahlapImportAdapterTest {
    @Test
    fun supplementalRatingTargetPagesAreImportedWithDifficultyPages() = runTest {
        val persistence = RealImportMemoryPersistence()
        val adapter = adapter(difficulties = listOf(Difficulty.BASIC))

        val result = adapter.importFetchedPages(
            source = "wahlap-supplemental-test",
            pageProvider = WahlapScorePageProvider { resourceText("wahlap_valid_fixture.html") },
            supplementalPageProvider = WahlapSupplementalPageProvider {
                listOf(
                    WahlapSupplementalPage(
                        label = "rating-target-music",
                        html = resourceText("wahlap_rating_target_supplemental_synthetic_fixture.html"),
                    ),
                )
            },
            persistence = persistence,
        )

        assertEquals(1, result.fetchedSupplementalPageCount)
        assertEquals(5, result.parsedSupplementalRecordCount)
        assertEquals(8, result.parsedRecordCount)
        assertEquals(8, result.importResult.inserted)
        assertEquals(0, result.importResult.quarantined)
        assertImportedScore("SYNTHETIC SONG ALPHA", Difficulty.EXPERT, SongType.DX, "12+", 100.6000, persistence)
        assertImportedScore("SYNTHETIC SONG BETA", Difficulty.MASTER, SongType.STANDARD, "13", 100.7500, persistence)
        assertImportedScore("SYNTHETIC SONG GAMMA", Difficulty.BASIC, SongType.DX, "4", 100.5043, persistence)
        assertImportedScore("SYNTHETIC SONG EPSILON", Difficulty.MASTER, SongType.DX, "13+", 100.9000, persistence)
    }

    private fun adapter(difficulties: List<Difficulty>): RealWahlapImportAdapter {
        var nextBatch = 0
        return RealWahlapImportAdapter(
            pipeline = FakeImportPipeline(
                clock = { 1234L },
                batchIdFactory = {
                    nextBatch += 1
                    "real-batch-$nextBatch"
                },
            ),
            difficulties = difficulties,
        )
    }

    private fun resourceText(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private fun assertImportedScore(
        title: String,
        difficulty: Difficulty,
        songType: SongType,
        level: String,
        achievement: Double,
        persistence: RealImportMemoryPersistence,
    ) {
        val score = persistence.scores.values.single { it.title == title }
        assertEquals(difficulty, score.difficulty)
        assertEquals(difficulty.levelIndex, score.levelIndex)
        assertEquals(songType, score.songType)
        assertEquals(level, score.level)
        assertEquals(achievement, score.achievement, 0.0001)
    }
}

private class RealImportMemoryPersistence : ImportPersistence {
    val scores = linkedMapOf<String, ScoreRecord>()
    val quarantineRecords = mutableListOf<QuarantineRecord>()
    val batches = mutableListOf<ImportBatch>()

    override suspend fun findExistingScoreIds(scoreIds: Set<String>): Set<String> =
        scoreIds.filter(scores::containsKey).toSet()

    override suspend fun insertScoreRecords(records: List<ScoreRecord>) {
        records.forEach { scores[it.id] = it }
    }

    override suspend fun insertQuarantineRecords(records: List<QuarantineRecord>) {
        quarantineRecords += records
    }

    override suspend fun insertImportBatch(batch: ImportBatch) {
        batches += batch
    }
}

package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.ImportResult
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WahlapImportPipelineTest {
    private val parser = WahlapFixtureParser()

    @Test
    fun blankTitleExpertCardEntersQuarantine() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(
            resourceText("wahlap_blank_title_with_signals.html"),
            Difficulty.EXPERT,
        )
        val result = pipeline.importParsedRecords("wahlap-expert-blank", parsed, persistence)

        assertEquals(0, result.inserted)
        assertEquals(1, result.quarantined)
        assertEquals(1, persistence.quarantineRecords.size)
        assertEquals(0, persistence.scores.size)
        val q = persistence.quarantineRecords.first()
        assertTrue(q.reason.contains("blank_title"))
        assertEquals(Difficulty.EXPERT, q.difficulty)
    }

    @Test
    fun blankTitleMasterCardEntersQuarantine() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(
            resourceText("wahlap_blank_title_with_signals.html"),
            Difficulty.MASTER,
        )
        val result = pipeline.importParsedRecords("wahlap-master-blank", parsed, persistence)

        assertEquals(0, result.inserted)
        assertEquals(1, result.quarantined)
        assertTrue(persistence.quarantineRecords.first().reason.contains("blank_title"))
        assertEquals(Difficulty.MASTER, persistence.quarantineRecords.first().difficulty)
    }

    @Test
    fun blankTitleScoreNotWrittenToMainScoreTable() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(
            resourceText("wahlap_blank_title_with_signals.html"),
            Difficulty.EXPERT,
        )
        pipeline.importParsedRecords("test", parsed, persistence)

        assertEquals(0, persistence.scores.size)
        assertTrue(persistence.scores.values.none { it.title.isBlank() })
    }

    @Test
    fun duplicateFixtureImportProducesNoExtraRows() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(
            resourceText("wahlap_duplicate_cards.html"),
            Difficulty.MASTER,
        )
        val first = pipeline.importParsedRecords("dup-first", parsed, persistence)
        val second = pipeline.importParsedRecords("dup-second", parsed, persistence)

        assertEquals(1, first.inserted)
        assertEquals(1, first.skippedDuplicate)
        assertEquals(0, second.inserted)
        assertEquals(1, second.updated)
        assertEquals(1, second.skippedDuplicate)
        assertEquals(1, persistence.scores.size)
    }

    @Test
    fun malformedHtmlDoesNotCrashPipeline() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(resourceText("wahlap_malformed.html"), Difficulty.BASIC)
        val result = pipeline.importParsedRecords("malformed", parsed, persistence)

        assertEquals(0, result.inserted)
        assertEquals(0, result.quarantined)
    }

    @Test
    fun validWahlapFixtureFlowsIntoScoreTable() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.EXPERT)
        val result = pipeline.importParsedRecords("valid-wahlap", parsed, persistence)

        assertEquals(3, result.inserted)
        assertEquals(0, result.quarantined)
        assertEquals(3, persistence.scores.size)
        assertTrue(persistence.scores.values.all { it.title.isNotBlank() })
        assertTrue(persistence.scores.values.all { it.levelIndex == 2 })
    }

    @Test
    fun partialFetchUpsertsReturnedScoresAndPreservesUntouchedHistory() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()
        val historicalScores = (0 until 10).map { index ->
            scoreRecord(
                title = "History $index",
                achievement = 90.0 + index,
                dxScore = 1000 + index,
            )
        }
        persistence.insertScoreRecords(historicalScores)

        val parsed = (0 until 3).map { index ->
            parsedScore(
                title = "History $index",
                achievement = 100.0 + index / 10.0,
                dxScore = 2000 + index,
            )
        }
        val result = pipeline.importParsedRecords("partial-fetch", parsed, persistence)

        assertEquals(0, result.inserted)
        assertEquals(3, result.updated)
        assertEquals(10, persistence.scores.size)
        (0 until 3).forEach { index ->
            val updated = persistence.scores.getValue(ScoreRecordIds.idFor("History $index", 2, SongType.DX))
            assertEquals(100.0 + index / 10.0, updated.achievement, 0.0001)
            assertEquals(2000 + index, updated.dxScore)
            assertEquals("batch-1", updated.sourceBatchId)
        }
        (3 until 10).forEach { index ->
            val untouched = persistence.scores.getValue(ScoreRecordIds.idFor("History $index", 2, SongType.DX))
            assertEquals(90.0 + index, untouched.achievement, 0.0001)
            assertEquals(1000 + index, untouched.dxScore)
            assertEquals("old-batch", untouched.sourceBatchId)
        }
    }

    @Test
    fun normalFetchUpdatesExistingScoresAndInsertsNewScores() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()
        val historicalScores = (0 until 3).map { index ->
            scoreRecord(
                title = "Normal $index",
                achievement = 95.0 + index,
                dxScore = 1500 + index,
            )
        }
        persistence.insertScoreRecords(historicalScores)

        val parsed = (0 until 5).map { index ->
            parsedScore(
                title = "Normal $index",
                achievement = 99.0 + index / 10.0,
                dxScore = 2500 + index,
            )
        }
        val result = pipeline.importParsedRecords("normal-fetch", parsed, persistence)

        assertEquals(2, result.inserted)
        assertEquals(3, result.updated)
        assertEquals(5, persistence.scores.size)
        (0 until 5).forEach { index ->
            val score = persistence.scores.getValue(ScoreRecordIds.idFor("Normal $index", 2, SongType.DX))
            assertEquals(99.0 + index / 10.0, score.achievement, 0.0001)
            assertEquals(2500 + index, score.dxScore)
            assertEquals("batch-1", score.sourceBatchId)
        }
    }

    @Test
    fun dxScoresForSdDxSongsDoNotWriteStandardChartIds() = runTest {
        val catalog = MaimaiSongCatalog.fromLxnsSongListJson(
            """
            {
              "songs": [
                {
                  "id": 1051,
                  "title": "Destr0yer",
                  "difficulties": {
                    "standard": [
                      {"difficulty": 3, "level": "14"}
                    ],
                    "dx": [
                      {"difficulty": 3, "level": "12+"}
                    ]
                  }
                },
                {
                  "id": 1052,
                  "title": "Oshama Scramble!",
                  "difficulties": {
                    "standard": [
                      {"difficulty": 3, "level": "14"}
                    ],
                    "dx": [
                      {"difficulty": 3, "level": "13+"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )
        val parser = WahlapFixtureParser(songCatalog = catalog)
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(
            """
            <form action="https://maimai.wahlap.com/maimai-mobile/record/musicDetail/" method="GET">
              <div class="music_lv_block">12+</div>
              <div class="music_name_block">Destr0yer</div>
              <div class="music_score_block w_112 t_r f_l f_12">99.6112%</div>
              <div class="music_score_block w_190 t_r f_l f_12">1,627 / 1,806</div>
              <img src="images/music_icon_fc.png" class="h_30 f_r">
              <img src="images/music_icon_sync.png" class="h_30 f_r">
            </form>
            <form action="https://maimai.wahlap.com/maimai-mobile/record/musicDetail/" method="GET">
              <div class="music_lv_block">13+</div>
              <div class="music_name_block">Oshama Scramble!</div>
              <div class="music_score_block w_112 t_r f_l f_12">98.9868%</div>
              <div class="music_score_block w_190 t_r f_l f_12">1,401 / 1,662</div>
              <img src="images/music_icon_sync.png" class="h_30 f_r">
            </form>
            """.trimIndent(),
            Difficulty.MASTER,
        )
        val result = pipeline.importParsedRecords("p1-sd-dx-regression", parsed, persistence)

        assertEquals(2, result.inserted)
        assertEquals(0, result.quarantined)
        val destr0yerDx = persistence.scores.getValue(ScoreRecordIds.idFor("Destr0yer", 3, SongType.DX))
        val oshamaDx = persistence.scores.getValue(ScoreRecordIds.idFor("Oshama Scramble!", 3, SongType.DX))
        assertEquals(99.6112, destr0yerDx.achievement, 0.0001)
        assertEquals(98.9868, oshamaDx.achievement, 0.0001)
        assertTrue(!persistence.scores.containsKey(ScoreRecordIds.idFor("Destr0yer", 3, SongType.STANDARD)))
        assertTrue(!persistence.scores.containsKey(ScoreRecordIds.idFor("Oshama Scramble!", 3, SongType.STANDARD)))
    }

    @Test
    fun importSummaryCountsReflectWahlapImport() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.MASTER)
        val result = pipeline.importParsedRecords("summary-test", parsed, persistence)
        val batch = persistence.batches.last()

        assertEquals(3, batch.totalParsed)
        assertEquals(result.inserted, batch.inserted)
        assertEquals(result.skippedDuplicate, batch.skippedDuplicate)
        assertEquals(result.quarantined, batch.quarantined)
        assertEquals(3, batch.inserted)
        assertEquals(0, batch.quarantined)
    }

    @Test
    fun blankTitleWithInvalidAchievementIsQuarantinedWithBothReasons() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(
            resourceText("wahlap_blank_title_invalid_achievement.html"),
            Difficulty.MASTER,
        )
        val result = pipeline.importParsedRecords("multi-reason", parsed, persistence)

        assertEquals(0, result.inserted)
        assertEquals(1, result.quarantined)
        val q = persistence.quarantineRecords.first()
        assertTrue(q.reason.contains("blank_title"))
        assertTrue(q.reason.contains("invalid_achievement"))
    }

    @Test
    fun wahlapFixtureDoesNotLeakRawHtmlIntoScores() = runTest {
        val persistence = InMemPersistence()
        val pipeline = deterministicPipeline()

        val parsed = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.EXPERT)
        pipeline.importParsedRecords("no-leak", parsed, persistence)

        val allScores = persistence.scores.values
        assertTrue(allScores.none { it.title.contains("<") })
        assertTrue(allScores.none { it.title.contains("form") })
        assertTrue(allScores.none { it.title.contains("html", ignoreCase = true) })
    }

    @Test
    fun wahlapFixtureContentDoesNotContainSensitiveTokens() = runTest {
        val html = resourceText("wahlap_valid_fixture.html")
        assertTrue(!html.contains("Cookie", ignoreCase = true))
        assertTrue(!html.contains("Token", ignoreCase = true))
        assertTrue(!html.contains("auth", ignoreCase = true))
    }

    private fun deterministicPipeline(): FakeImportPipeline {
        var nextBatch = 0
        return FakeImportPipeline(
            clock = { 1234L },
            batchIdFactory = {
                nextBatch += 1
                "batch-$nextBatch"
            },
        )
    }

    private fun resourceText(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private fun scoreRecord(
        title: String,
        achievement: Double,
        dxScore: Int,
        songType: SongType = SongType.DX,
        difficulty: Difficulty = Difficulty.EXPERT,
    ): ScoreRecord =
        ScoreRecord(
            id = ScoreRecordIds.idFor(title, difficulty.levelIndex, songType),
            title = title,
            songType = songType,
            difficulty = difficulty,
            level = "12+",
            levelIndex = difficulty.levelIndex,
            achievement = achievement,
            dxScore = dxScore,
            fc = null,
            fs = null,
            sourceBatchId = "old-batch",
            importedAt = 1L,
        )

    private fun parsedScore(
        title: String,
        achievement: Double,
        dxScore: Int,
        songType: SongType = SongType.DX,
        difficulty: Difficulty = Difficulty.EXPERT,
    ): ParsedScoreRecord =
        ParsedScoreRecord(
            title = title,
            songType = songType,
            difficulty = difficulty,
            level = "12+",
            levelIndex = difficulty.levelIndex,
            achievement = achievement,
            dxScore = dxScore,
            fc = null,
            fs = null,
            rawFingerprint = "raw-$title-$achievement-$dxScore",
        )
}

private class InMemPersistence : ImportPersistence {
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

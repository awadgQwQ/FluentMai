package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.ImportResult
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
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
        assertEquals(2, second.skippedDuplicate)
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
}

private class InMemPersistence : ImportPersistence {
    val scores = linkedMapOf<String, ScoreRecord>()
    val quarantineRecords = mutableListOf<QuarantineRecord>()
    val batches = mutableListOf<ImportBatch>()

    override suspend fun findExistingScoreIds(scoreIds: Set<String>): Set<String> =
        scoreIds.filter(scores::containsKey).toSet()

    override suspend fun insertScoreRecords(records: List<ScoreRecord>) {
        records.forEach { scores.putIfAbsent(it.id, it) }
    }

    override suspend fun insertQuarantineRecords(records: List<QuarantineRecord>) {
        quarantineRecords += records
    }

    override suspend fun insertImportBatch(batch: ImportBatch) {
        batches += batch
    }
}

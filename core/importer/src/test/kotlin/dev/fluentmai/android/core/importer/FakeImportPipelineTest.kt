package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeImportPipelineTest {
    @Test
    fun importsValidFixtureAndSkipsRepeatDuplicates() = runTest {
        val persistence = InMemoryImportPersistence()
        val pipeline = deterministicPipeline()
        val fixture = resourceText("valid_sample_import.json")

        val first = pipeline.importJson("valid", fixture, persistence)
        val second = pipeline.importJson("valid", fixture, persistence)

        assertEquals(3, first.inserted)
        assertEquals(0, first.skippedDuplicate)
        assertEquals(0, first.quarantined)
        assertEquals(0, second.inserted)
        assertEquals(3, second.skippedDuplicate)
        assertEquals(3, persistence.scores.size)
    }

    @Test
    fun routesInvalidRecordsToQuarantineOnly() = runTest {
        val persistence = InMemoryImportPersistence()
        val pipeline = deterministicPipeline()

        pipeline.importJson("blank", resourceText("blank_title_quarantine_case.json"), persistence)
        pipeline.importJson("achievement", resourceText("invalid_achievement_case.json"), persistence)
        pipeline.importJson("levelIndex", resourceText("invalid_level_index_case.json"), persistence)

        assertEquals(0, persistence.scores.size)
        assertEquals(3, persistence.quarantineRecords.size)
        assertTrue(persistence.quarantineRecords.any { it.reason.contains("blank_title") })
        assertTrue(persistence.quarantineRecords.any { it.reason.contains("invalid_achievement") })
        assertTrue(persistence.quarantineRecords.any { it.reason.contains("invalid_level_index") })
        assertTrue(persistence.scores.values.none { it.title.isBlank() })
        assertTrue(persistence.scores.values.none { it.achievement < 0.0 || it.achievement > 101.0 })
        assertTrue(persistence.scores.values.none { it.levelIndex !in 0..4 })
    }

    @Test
    fun skipsDuplicateRecordsWithinSingleFixture() = runTest {
        val persistence = InMemoryImportPersistence()
        val pipeline = deterministicPipeline()

        val result = pipeline.importJson("duplicate", resourceText("duplicate_import_case.json"), persistence)

        assertEquals(1, result.inserted)
        assertEquals(1, result.skippedDuplicate)
        assertEquals(1, persistence.scores.size)
    }

    @Test
    fun importSummaryCountsMatchActualResults() = runTest {
        val persistence = InMemoryImportPersistence()
        val pipeline = deterministicPipeline()

        val result = pipeline.importJson("counts", resourceText("valid_sample_import.json"), persistence)
        val batch = persistence.batches.last()

        assertEquals(3, batch.totalParsed)
        assertEquals(result.inserted, batch.inserted)
        assertEquals(result.skippedDuplicate, batch.skippedDuplicate)
        assertEquals(result.quarantined, batch.quarantined)
        assertEquals(3, batch.inserted)
        assertEquals(0, batch.skippedDuplicate)
        assertEquals(0, batch.quarantined)
    }

    @Test
    fun blankTitleScoreNotWrittenToMainTable() = runTest {
        val persistence = InMemoryImportPersistence()
        val pipeline = deterministicPipeline()

        pipeline.importJson("blank", resourceText("blank_title_quarantine_case.json"), persistence)

        assertEquals(0, persistence.scores.size)
        assertEquals(1, persistence.quarantineRecords.size)
        val q = persistence.quarantineRecords.first()
        assertTrue(q.reason.contains("blank_title"))
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

private class InMemoryImportPersistence : ImportPersistence {
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


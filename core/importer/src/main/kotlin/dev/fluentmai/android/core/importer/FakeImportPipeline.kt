package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.ImportResult
import dev.fluentmai.android.core.model.QuarantineRecord
import java.util.UUID

class FakeImportPipeline(
    private val parser: FixtureImportParser = FixtureImportParser(),
    private val validator: ScoreRecordValidator = ScoreRecordValidator(),
    private val deduplicator: ImportDeduplicator = ImportDeduplicator(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val batchIdFactory: () -> String = { UUID.randomUUID().toString() },
) {
    suspend fun importJson(
        source: String,
        json: String,
        persistence: ImportPersistence,
    ): ImportResult {
        val parsed = runCatching { parser.parse(json) }.getOrElse {
            val batchId = batchIdFactory()
            val importedAt = clock()
            val result = ImportResult(
                batchId = batchId,
                inserted = 0,
                updated = 0,
                skippedDuplicate = 0,
                quarantined = 0,
                rejected = 1,
            )
            persistence.insertImportBatch(result.toBatch(source, importedAt, totalParsed = 0))
            return result
        }
        return importParsedRecords(source, parsed, persistence)
    }

    suspend fun importParsedRecords(
        source: String,
        parsed: List<ParsedScoreRecord>,
        persistence: ImportPersistence,
    ): ImportResult {
        val batchId = batchIdFactory()
        val importedAt = clock()

        val outcomes = parsed.map(validator::validate)
        val validDrafts = outcomes.filterIsInstance<ValidationOutcome.Valid>().map { it.draft }
        val invalid = outcomes.filterIsInstance<ValidationOutcome.Invalid>()
        val existingIds = persistence.findExistingScoreIds(validDrafts.map { it.id }.toSet())
        val deduped = deduplicator.deduplicate(validDrafts)
        val scoreRecords = deduped.accepted.map { it.toScoreRecord(batchId, importedAt) }
        val updated = deduped.accepted.count { it.id in existingIds }
        val quarantineRecords = invalid.mapIndexed { index, item ->
            QuarantineRecord(
                id = "quarantine-$batchId-$index-${item.parsed.rawFingerprint.take(16)}",
                reason = item.reasons.joinToString(separator = "|"),
                difficulty = item.parsed.difficulty,
                rawFingerprint = item.parsed.rawFingerprint,
                sourceBatchId = batchId,
                createdAt = importedAt,
            )
        }

        val result = ImportResult(
            batchId = batchId,
            inserted = scoreRecords.size - updated,
            updated = updated,
            skippedDuplicate = deduped.skippedDuplicate,
            quarantined = quarantineRecords.size,
            rejected = 0,
        )

        persistence.insertScoreRecords(scoreRecords)
        persistence.insertQuarantineRecords(quarantineRecords)
        persistence.insertImportBatch(result.toBatch(source, importedAt, totalParsed = parsed.size))

        return result
    }

    private fun ImportResult.toBatch(source: String, importedAt: Long, totalParsed: Int): ImportBatch =
        ImportBatch(
            id = batchId,
            source = source,
            importedAt = importedAt,
            totalParsed = totalParsed,
            inserted = inserted,
            updated = updated,
            skippedDuplicate = skippedDuplicate,
            quarantined = quarantined,
            rejected = rejected,
        )
}

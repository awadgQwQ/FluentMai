package dev.fluentmai.android.core.database

import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord

class FluentMaiRepository(
    private val database: FluentMaiDatabase,
) {
    suspend fun scoreCount(): Int =
        database.scoreRecordDao().count()

    suspend fun scores(): List<ScoreRecord> =
        database.scoreRecordDao().getAll().map(ScoreRecordEntity::toModel)

    suspend fun quarantineCount(): Int =
        database.quarantineRecordDao().count()

    suspend fun quarantineRecords(): List<QuarantineRecord> =
        database.quarantineRecordDao().getAll().map(QuarantineRecordEntity::toModel)

    suspend fun latestImportBatch(): ImportBatch? =
        database.importBatchDao().latest()?.toModel()

    suspend fun replaceLatestWahlapScorePages(
        batchId: String,
        pages: List<CachedWahlapScorePage>,
    ) {
        database.wahlapScorePageDao().deleteAll()
        if (pages.isNotEmpty()) {
            database.wahlapScorePageDao().insertAll(pages.map { it.toEntity(sourceBatchId = batchId) })
        }
    }

    suspend fun latestWahlapScorePages(): List<CachedWahlapScorePage> {
        val latestBatch = database.importBatchDao().latest() ?: return emptyList()
        return database.wahlapScorePageDao().forBatch(latestBatch.id).map(WahlapScorePageEntity::toModel)
    }
}

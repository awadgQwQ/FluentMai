package dev.fluentmai.android.core.database

import dev.fluentmai.android.core.importer.MaimaiSongCatalog
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType

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

    suspend fun deleteScoresNotInCatalog(catalog: MaimaiSongCatalog): Int {
        val invalidIds = database.scoreRecordDao().getAll()
            .filter { entity ->
                val songType = runCatching { SongType.valueOf(entity.songType) }.getOrNull()
                    ?: return@filter false
                catalog.chartExists(entity.title, entity.levelIndex, songType) == false
            }
            .map { it.id }

        return if (invalidIds.isEmpty()) {
            0
        } else {
            database.scoreRecordDao().deleteByIds(invalidIds)
        }
    }
}

package dev.fluentmai.android.core.database

import dev.fluentmai.android.core.importer.ImportPersistence
import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord

class RoomImportPersistence(
    private val database: FluentMaiDatabase,
) : ImportPersistence {
    override suspend fun findExistingScoreIds(scoreIds: Set<String>): Set<String> =
        if (scoreIds.isEmpty()) {
            emptySet()
        } else {
            database.scoreRecordDao().findIds(scoreIds.toList()).toSet()
        }

    override suspend fun insertScoreRecords(records: List<ScoreRecord>) {
        if (records.isNotEmpty()) {
            database.scoreRecordDao().insertAll(records.map(ScoreRecord::toEntity))
        }
    }

    override suspend fun insertQuarantineRecords(records: List<QuarantineRecord>) {
        if (records.isNotEmpty()) {
            database.quarantineRecordDao().insertAll(records.map(QuarantineRecord::toEntity))
        }
    }

    override suspend fun insertImportBatch(batch: ImportBatch) {
        database.importBatchDao().insert(batch.toEntity())
    }
}

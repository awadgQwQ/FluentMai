package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.ImportBatch
import dev.fluentmai.android.core.model.QuarantineRecord
import dev.fluentmai.android.core.model.ScoreRecord

interface ImportPersistence {
    suspend fun findExistingScoreIds(scoreIds: Set<String>): Set<String>
    suspend fun insertScoreRecords(records: List<ScoreRecord>)
    suspend fun insertQuarantineRecords(records: List<QuarantineRecord>)
    suspend fun insertImportBatch(batch: ImportBatch)
}


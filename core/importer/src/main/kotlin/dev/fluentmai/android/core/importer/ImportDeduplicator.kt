package dev.fluentmai.android.core.importer

data class DeduplicationResult(
    val accepted: List<ScoreRecordDraft>,
    val skippedDuplicate: Int,
)

class ImportDeduplicator {
    fun deduplicate(drafts: List<ScoreRecordDraft>): DeduplicationResult {
        val seen = mutableSetOf<String>()
        val accepted = mutableListOf<ScoreRecordDraft>()
        var skipped = 0

        drafts.forEach { draft ->
            if (seen.add(draft.id)) {
                accepted += draft
            } else {
                skipped += 1
            }
        }

        return DeduplicationResult(
            accepted = accepted,
            skippedDuplicate = skipped,
        )
    }
}

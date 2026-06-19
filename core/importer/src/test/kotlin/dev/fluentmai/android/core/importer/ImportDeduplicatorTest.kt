package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportDeduplicatorTest {
    private val deduplicator = ImportDeduplicator()

    @Test
    fun skipsExistingAndWithinBatchDuplicates() {
        val first = draft("Song A", 3)
        val sameAsFirst = draft("Song A", 3)
        val alreadyStored = draft("Song B", 2)

        val result = deduplicator.deduplicate(
            drafts = listOf(first, sameAsFirst, alreadyStored),
            existingScoreIds = setOf(alreadyStored.id),
        )

        assertEquals(listOf(first), result.accepted)
        assertEquals(2, result.skippedDuplicate)
    }

    private fun draft(title: String, levelIndex: Int): ScoreRecordDraft =
        ScoreRecordDraft(
            id = ScoreRecordIds.idFor(title, levelIndex),
            title = title,
            difficulty = Difficulty.fromLevelIndex(levelIndex) ?: Difficulty.BASIC,
            level = "12",
            levelIndex = levelIndex,
            achievement = 99.0,
            dxScore = 2000,
            fc = null,
            fs = null,
        )
}


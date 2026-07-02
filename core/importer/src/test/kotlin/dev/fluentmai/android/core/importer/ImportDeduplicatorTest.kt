package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportDeduplicatorTest {
    private val deduplicator = ImportDeduplicator()

    @Test
    fun skipsWithinBatchDuplicatesButKeepsExistingForUpdate() {
        val first = draft("Song A", 3)
        val sameAsFirst = draft("Song A", 3)
        val alreadyStored = draft("Song B", 2)

        val result = deduplicator.deduplicate(listOf(first, sameAsFirst, alreadyStored))

        assertEquals(listOf(first, alreadyStored), result.accepted)
        assertEquals(1, result.skippedDuplicate)
    }

    private fun draft(title: String, levelIndex: Int): ScoreRecordDraft =
        ScoreRecordDraft(
            id = ScoreRecordIds.idFor(title, levelIndex),
            songId = null,
            title = title,
            songType = SongType.STANDARD,
            difficulty = Difficulty.fromLevelIndex(levelIndex) ?: Difficulty.BASIC,
            level = "12",
            levelIndex = levelIndex,
            achievement = 99.0,
            dxScore = 2000,
            fc = null,
            fs = null,
        )
}

package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreRecordValidatorTest {
    private val validator = ScoreRecordValidator()

    @Test
    fun acceptsValidRecord() {
        val outcome = validator.validate(validParsedRecord())

        assertTrue(outcome is ValidationOutcome.Valid)
        val draft = (outcome as ValidationOutcome.Valid).draft
        assertEquals("Valid Song", draft.title)
        assertEquals(Difficulty.EXPERT, draft.difficulty)
        assertEquals(2, draft.levelIndex)
    }

    @Test
    fun rejectsBlankTitle() {
        val outcome = validator.validate(validParsedRecord(title = " "))

        assertInvalidReason(outcome, "blank_title")
    }

    @Test
    fun rejectsInvalidAchievement() {
        val outcome = validator.validate(validParsedRecord(achievement = 101.5001))

        assertInvalidReason(outcome, "invalid_achievement")
    }

    @Test
    fun rejectsInvalidLevelIndex() {
        val outcome = validator.validate(validParsedRecord(levelIndex = 5))

        assertInvalidReason(outcome, "invalid_level_index")
    }

    private fun assertInvalidReason(outcome: ValidationOutcome, reason: String) {
        assertTrue(outcome is ValidationOutcome.Invalid)
        assertTrue((outcome as ValidationOutcome.Invalid).reasons.contains(reason))
    }

    private fun validParsedRecord(
        title: String = "Valid Song",
        achievement: Double = 100.0000,
        levelIndex: Int = 2,
    ): ParsedScoreRecord =
        ParsedScoreRecord(
            title = title,
            difficulty = Difficulty.fromLevelIndex(levelIndex),
            level = "12+",
            levelIndex = levelIndex,
            achievement = achievement,
            dxScore = 2400,
            fc = "FC",
            fs = null,
            rawFingerprint = "fingerprint",
        )
}


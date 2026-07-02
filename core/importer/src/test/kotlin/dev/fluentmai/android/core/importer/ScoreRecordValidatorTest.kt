package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
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
        assertEquals(SongType.STANDARD, draft.songType)
        assertEquals(Difficulty.EXPERT, draft.difficulty)
        assertEquals(2, draft.levelIndex)
    }

    @Test
    fun scoreIdIncludesSongType() {
        val standard = validator.validate(validParsedRecord(songType = SongType.STANDARD))
        val dx = validator.validate(validParsedRecord(songType = SongType.DX))

        val standardDraft = (standard as ValidationOutcome.Valid).draft
        val dxDraft = (dx as ValidationOutcome.Valid).draft
        assertTrue(standardDraft.id != dxDraft.id)
        assertEquals(SongType.DX, dxDraft.songType)
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
    fun acceptsMaimaiDxAchievementsAbove100Percent() {
        val over100 = validator.validate(validParsedRecord(achievement = 100.7336))
        val exact101 = validator.validate(validParsedRecord(achievement = 101.0))

        assertTrue(over100 is ValidationOutcome.Valid)
        assertTrue(exact101 is ValidationOutcome.Valid)
    }

    @Test
    fun rejectsInvalidLevelIndex() {
        val outcome = validator.validate(validParsedRecord(levelIndex = 5))

        assertInvalidReason(outcome, "invalid_level_index")
    }

    @Test
    fun blankTitleExpertCardEntersQuarantine() {
        val outcome = validator.validate(
            ParsedScoreRecord(
                title = "   ",
                difficulty = Difficulty.EXPERT,
                level = "12+",
                levelIndex = 2,
                achievement = 98.0,
                dxScore = 2500,
                fc = null,
                fs = null,
                rawFingerprint = "fp-expert-blank",
            )
        )

        assertInvalidReason(outcome, "blank_title")
    }

    @Test
    fun blankTitleMasterCardEntersQuarantine() {
        val outcome = validator.validate(
            ParsedScoreRecord(
                title = "",
                difficulty = Difficulty.MASTER,
                level = "14",
                levelIndex = 3,
                achievement = 99.5,
                dxScore = 3000,
                fc = "FC",
                fs = null,
                rawFingerprint = "fp-master-blank",
            )
        )

        assertInvalidReason(outcome, "blank_title")
    }

    @Test
    fun rejectsDifficultyLevelIndexMismatch() {
        val outcome = validator.validate(
            ParsedScoreRecord(
                title = "Cross Polluted",
                difficulty = Difficulty.BASIC,
                level = "13",
                levelIndex = 3,
                achievement = 97.0,
                dxScore = 2000,
                fc = null,
                fs = null,
                rawFingerprint = "fp-cross",
            )
        )

        assertInvalidReason(outcome, "difficulty_level_index_mismatch")
    }

    @Test
    fun rejectsNegativeAchievement() {
        val outcome = validator.validate(validParsedRecord(achievement = -0.0001))

        assertInvalidReason(outcome, "invalid_achievement")
    }

    @Test
    fun rejectsNonFiniteAchievement() {
        val outcome = validator.validate(validParsedRecord(achievement = Double.NaN))

        assertInvalidReason(outcome, "invalid_achievement")
    }

    private fun assertInvalidReason(outcome: ValidationOutcome, reason: String) {
        assertTrue(outcome is ValidationOutcome.Invalid)
        assertTrue((outcome as ValidationOutcome.Invalid).reasons.contains(reason))
    }

    private fun validParsedRecord(
        title: String = "Valid Song",
        achievement: Double = 100.0000,
        levelIndex: Int = 2,
        songType: SongType = SongType.STANDARD,
    ): ParsedScoreRecord =
        ParsedScoreRecord(
            title = title,
            songType = songType,
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

package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WahlapFixtureParserTest {
    private val parser = WahlapFixtureParser()

    @Test
    fun parsesValidExpertFixtureRecords() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.EXPERT)

        assertEquals(3, records.size)

        val first = records[0]
        assertEquals("PANDORA PARADOXXX", first.title)
        assertEquals(Difficulty.EXPERT, first.difficulty)
        assertEquals(2, first.levelIndex)
        assertEquals(100.5000, first.achievement ?: 0.0, 0.0001)
        assertEquals(3120, first.dxScore)
        assertEquals("fc", first.fc)
        assertEquals("fs", first.fs)
        assertNotNull(first.rawFingerprint)
    }

    @Test
    fun mapsBasicDifficultyToLevelIndex0() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.BASIC)
        assertEquals(3, records.size)
        records.forEach { assertEquals(0, it.levelIndex) }
    }

    @Test
    fun mapsAdvancedDifficultyToLevelIndex1() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.ADVANCED)
        assertEquals(3, records.size)
        records.forEach { assertEquals(1, it.levelIndex) }
    }

    @Test
    fun mapsExpertDifficultyToLevelIndex2() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.EXPERT)
        assertEquals(3, records.size)
        records.forEach { assertEquals(2, it.levelIndex) }
    }

    @Test
    fun mapsMasterDifficultyToLevelIndex3() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.MASTER)
        assertEquals(3, records.size)
        records.forEach { assertEquals(3, it.levelIndex) }
    }

    @Test
    fun mapsReMasterDifficultyToLevelIndex4() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.RE_MASTER)
        assertEquals(3, records.size)
        records.forEach { assertEquals(4, it.levelIndex) }
    }

    @Test
    fun blankTitleCardHasNullTitle() {
        val records = parser.parse(
            resourceText("wahlap_blank_title_with_signals.html"),
            Difficulty.EXPERT,
        )

        assertEquals(1, records.size)
        assertNull(records[0].title)
    }

    @Test
    fun emptyHtmlReturnsEmptyList() {
        val records = parser.parse("", Difficulty.BASIC)
        assertTrue(records.isEmpty())
    }

    @Test
    fun malformedHtmlReturnsEmptyListWithoutCrashing() {
        val records = parser.parse(resourceText("wahlap_malformed.html"), Difficulty.BASIC)
        assertTrue(records.isEmpty())
    }

    @Test
    fun extractsFcApFromStatusIcons() {
        val records = parser.parse(resourceText("wahlap_valid_fixture.html"), Difficulty.EXPERT)

        assertEquals("fc", records[0].fc)
        assertEquals("fs", records[0].fs)
        assertEquals("fs", records[1].fs)
        assertNull(records[1].fc)
        assertEquals("fcp", records[2].fc)
        assertNull(records[2].fs)
    }

    @Test
    fun parsesMixedDifficultyRatingTargetPage() {
        val records = parser.parseMixedDifficultyPage(
            """
            <form action="https://maimai.wahlap.com/maimai-mobile/record/musicDetail/" method="GET">
              <input type="hidden" name="idx" value="synthetic-inline-001">
              <input type="hidden" name="diff" value="4">
              <div class="music_name_block t_l f_13 break">SYNTHETIC INLINE RE MASTER</div>
              <div class="music_score_block w_112 t_r f_l f_12">100.6215%</div>
              <div class="music_lv_block">等级 13</div>
              <img class="music_kind_icon" src="/maimai-mobile/img/music_dx.png">
            </form>
            <form action="https://maimai.wahlap.com/maimai-mobile/record/musicDetail/" method="GET">
              <input type="hidden" name="idx" value="synthetic-inline-002">
              <img src="/maimai-mobile/img/diff_expert.png">
              <div class="music_name_block t_l f_13 break">SYNTHETIC INLINE EXPERT</div>
              <div class="music_score_block w_150 t_l f_r f_12 p_r">100.5386%</div>
              <div class="music_lv_block">EXPERT 专家 等级 12+</div>
              <img class="music_kind_icon" src="/maimai-mobile/img/music_dx.png">
            </form>
            """.trimIndent(),
        )

        assertEquals(2, records.size)
        assertEquals(Difficulty.RE_MASTER, records[0].difficulty)
        assertEquals(4, records[0].levelIndex)
        assertEquals("13", records[0].level)
        assertEquals("SYNTHETIC INLINE EXPERT", records[1].title)
        assertEquals(Difficulty.EXPERT, records[1].difficulty)
        assertEquals(2, records[1].levelIndex)
        assertEquals("12+", records[1].level)
        assertEquals(SongType.DX, records[1].songType)
        assertEquals(100.5386, records[1].achievement ?: 0.0, 0.0001)
    }

    @Test
    fun parsesSyntheticRatingTargetSupplementalCards() {
        val records = parser.parseMixedDifficultyPage(
            resourceText("wahlap_rating_target_supplemental_synthetic_fixture.html"),
        )

        assertEquals(5, records.size)
        assertSupplementalScore(records, "SYNTHETIC SONG ALPHA", Difficulty.EXPERT, 2, "12+", SongType.DX, 100.6000)
        assertSupplementalScore(records, "SYNTHETIC SONG BETA", Difficulty.MASTER, 3, "13", SongType.STANDARD, 100.7500)
        assertSupplementalScore(records, "SYNTHETIC SONG GAMMA", Difficulty.BASIC, 0, "4", SongType.DX, 100.5043)
        assertSupplementalScore(records, "SYNTHETIC SONG DELTA", Difficulty.EXPERT, 2, "12", SongType.STANDARD, 100.5386)
        assertSupplementalScore(records, "SYNTHETIC SONG EPSILON", Difficulty.MASTER, 3, "13+", SongType.DX, 100.9000)
    }

    @Test
    fun blankHtmlReturnsEmptyList() {
        val records = parser.parse("   ", Difficulty.MASTER)
        assertTrue(records.isEmpty())
    }

    private fun resourceText(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()

    private fun assertSupplementalScore(
        records: List<ParsedScoreRecord>,
        title: String,
        difficulty: Difficulty,
        levelIndex: Int,
        level: String,
        songType: SongType,
        achievement: Double,
    ) {
        val record = records.single { it.title == title }
        assertEquals(difficulty, record.difficulty)
        assertEquals(levelIndex, record.levelIndex)
        assertEquals(level, record.level)
        assertEquals(songType, record.songType)
        assertEquals(achievement, record.achievement ?: 0.0, 0.0001)
    }
}

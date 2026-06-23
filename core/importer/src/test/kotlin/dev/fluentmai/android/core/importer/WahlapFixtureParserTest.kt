package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
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
    fun blankHtmlReturnsEmptyList() {
        val records = parser.parse("   ", Difficulty.MASTER)
        assertTrue(records.isEmpty())
    }

    private fun resourceText(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()
}

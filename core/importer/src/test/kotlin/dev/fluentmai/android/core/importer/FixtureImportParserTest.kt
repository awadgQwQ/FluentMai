package dev.fluentmai.android.core.importer

import dev.fluentmai.android.core.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class FixtureImportParserTest {
    private val parser = FixtureImportParser()

    @Test
    fun parsesValidFixtureRecords() {
        val records = parser.parse(resourceText("valid_sample_import.json"))

        assertEquals(3, records.size)
        assertEquals("PANDORA PARADOXXX", records.first().title)
        assertEquals(Difficulty.MASTER, records.first().difficulty)
        assertEquals(3, records.first().levelIndex)
        assertEquals(100.5000, records.first().achievement ?: 0.0, 0.0001)
        assertNotNull(records.first().rawFingerprint)
    }

    private fun resourceText(name: String): String =
        requireNotNull(javaClass.classLoader?.getResource(name)).readText()
}


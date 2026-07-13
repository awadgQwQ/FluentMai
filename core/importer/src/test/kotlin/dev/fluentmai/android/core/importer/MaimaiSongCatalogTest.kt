package dev.fluentmai.android.core.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import dev.fluentmai.android.core.model.SongType

class MaimaiSongCatalogTest {
    @Test
    fun parsesLxnsSongListTitlesIdsAndCharts() {
        val catalog = MaimaiSongCatalog.fromLxnsSongListJson(
            """
            {
              "versions": [
                {"title": "maimai", "version": 10000},
                {"title": "舞萌DX 2026", "version": 25500}
              ],
              "songs": [
                {
                  "id": 834,
                  "title": "PANDORA PARADOXXX",
                  "artist": "削除",
                  "genre": "maimai",
                  "bpm": 150,
                  "version": 10000,
                  "difficulties": {
                    "standard": [
                      {"difficulty": 0, "level": "7+", "level_value": 7.7, "note_designer": "-", "version": 10000},
                      {"difficulty": 1, "level": "11", "level_value": 11.0, "note_designer": "-", "version": 10000},
                      {"difficulty": 2, "level": "13+", "level_value": 13.8, "note_designer": "譜面-100号", "version": 10000},
                      {"difficulty": 3, "level": "14+", "level_value": 14.9, "note_designer": "7.3GHz", "version": 10000,
                        "notes": {"total": 1000, "tap": 800, "hold": 50, "slide": 100, "touch": 0, "break": 50}}
                    ],
                    "dx": []
                  }
                },
                {
                  "id": 835,
                  "title": "TEmPTaTiON",
                  "version": 25500,
                  "difficulties": {
                    "standard": [],
                    "dx": [
                      {"level": "3"},
                      {"level": "7"},
                      {"level": "10"},
                      {"level": "13"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(834, catalog.idForTitle("PANDORA PARADOXXX"))
        assertEquals(835, catalog.idForTitle(" temptation "))
        assertNull(catalog.idForTitle("missing"))
        assertEquals(listOf(10000, 25500), catalog.majorVersions().map { it.id })
        assertEquals("舞萌DX 2026", catalog.majorVersions().last().name)
        assertEquals("14+", catalog.levelForTitle("PANDORA PARADOXXX", 3, SongType.STANDARD))
        assertTrue(catalog.chartExists("PANDORA PARADOXXX", 3, SongType.STANDARD) == true)
        assertFalse(catalog.chartExists("PANDORA PARADOXXX", 3, SongType.DX) == true)

        val chart = catalog.charts().single { it.title == "PANDORA PARADOXXX" && it.levelIndex == 3 }
        assertEquals(14.9, chart.levelValue ?: 0.0, 0.0001)
        assertEquals("7.3GHz", chart.noteDesigner)
        assertEquals("maimai", chart.chartVersionName)
        assertEquals(1000, chart.notes?.total)
    }

    @Test
    fun correctsDetectedTypeWhenOnlyOtherChartExists() {
        val catalog = MaimaiSongCatalog.fromLxnsSongListJson(
            """
            {
              "songs": [
                {
                  "id": 1835,
                  "title": "DX Only",
                  "difficulties": {
                    "standard": [],
                    "dx": [
                      {"level": "3"},
                      {"level": "6"},
                      {"level": "9+"},
                      {"level": "11+"},
                      {"level": "13"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(SongType.DX, catalog.resolveSongType("DX Only", 4, SongType.STANDARD))
        assertEquals("13", catalog.levelForTitle("DX Only", 4, SongType.DX))
    }

    @Test
    fun correctsDetectedTypeByLevelWhenBothSdAndDxChartsExist() {
        val catalog = MaimaiSongCatalog.fromLxnsSongListJson(
            """
            {
              "songs": [
                {
                  "id": 1051,
                  "title": "Destr0yer",
                  "difficulties": {
                    "standard": [
                      {"difficulty": 3, "level": "14"}
                    ],
                    "dx": [
                      {"difficulty": 3, "level": "12+"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(SongType.DX, catalog.resolveSongType("Destr0yer", 3, SongType.STANDARD, "12+"))
        assertEquals(SongType.STANDARD, catalog.resolveSongType("Destr0yer", 3, SongType.DX, "14"))
    }

    @Test
    fun normalizesAngstromTitleVariants() {
        val catalog = MaimaiSongCatalog.fromLxnsSongListJson(
            """
            {
              "songs": [
                {
                  "id": 1809,
                  "title": "Åntinomiε",
                  "difficulties": {
                    "standard": [],
                    "dx": [
                      {"difficulty": 0, "level": "6"},
                      {"difficulty": 1, "level": "9"},
                      {"difficulty": 2, "level": "12+"}
                    ]
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(1809, catalog.idForTitle("Åntinomiε"))
        assertEquals("12+", catalog.levelForTitle("Åntinomiε", 2, SongType.DX))
    }
}

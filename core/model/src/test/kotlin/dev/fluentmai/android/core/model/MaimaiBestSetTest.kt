package dev.fluentmai.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MaimaiBestSetTest {
    @Test
    fun futureCatalogRowsDoNotEmptyOrRedefineCurrentBest15() {
        val current = MaimaiCurrentVersion(
            majorVersion = MaimaiMajorVersion(25500, "舞萌DX 2026"),
            source = MaimaiCurrentVersionSource.CATALOG_VERSION_TABLE,
        )
        val oldScores = (0 until 40).map { ratedScore("old-$it", 25000, 400 - it) }
        val currentScores = (0 until 20).map { ratedScore("current-$it", 25500, 500 - it) }
        val futureScores = (0 until 5).map { ratedScore("future-$it", 25501, 900 - it) }
        val input = oldScores + currentScores + futureScores

        val result = buildMaimaiBestSet(input, current)

        assertEquals(15, result.newBest.size)
        assertEquals(35, result.oldBest.size)
        assertTrue(result.newBest.all { it.chart?.chartVersion == 25500 })
        assertTrue(result.oldBest.all { (it.chart?.chartVersion ?: Int.MAX_VALUE) < 25500 })
        assertFalse(result.all.any { it.chart?.chartVersion == 25501 })
        assertEquals(futureScores, result.ineligible)
        assertTrue(result.rating > 0)
        assertEquals(65, input.size)
    }

    @Test
    fun missingCurrentVersionMetadataFailsClosed() {
        val scores = listOf(
            ratedScore("old", 25000, 300),
            ratedScore("future", 25501, 400),
        )

        val result = buildMaimaiBestSet(scores, currentVersion = null)

        assertTrue(result.newBest.isEmpty())
        assertTrue(result.oldBest.isEmpty())
        assertEquals(scores, result.ineligible)
    }

    @Test
    fun oldSongReceivingCurrentChartBelongsToCurrentBucket() {
        val current = MaimaiCurrentVersion(
            majorVersion = MaimaiMajorVersion(25500, "舞萌DX 2026"),
            source = MaimaiCurrentVersionSource.CATALOG_VERSION_TABLE,
        )
        val rated = ratedScore("remaster", chartVersion = 25500, rating = 321).copy(
            chart = chart("remaster", songVersion = 11000, chartVersion = 25500),
        )

        val result = buildMaimaiBestSet(listOf(rated), current)

        assertEquals(listOf(rated), result.newBest)
        assertTrue(result.oldBest.isEmpty())
    }

    private fun ratedScore(id: String, chartVersion: Int, rating: Int): MaimaiRatedScore {
        val chart = chart(id, songVersion = chartVersion, chartVersion = chartVersion)
        return MaimaiRatedScore(
            score = ScoreRecord(
                id = id,
                songId = chart.songId,
                title = chart.title,
                songType = chart.songType,
                difficulty = chart.difficulty,
                level = chart.level,
                levelIndex = chart.levelIndex,
                achievement = 100.5,
                dxScore = null,
                fc = null,
                fs = null,
                sourceBatchId = "batch",
                importedAt = 1L,
            ),
            chart = chart,
            rating = rating,
        )
    }

    private fun chart(id: String, songVersion: Int, chartVersion: Int): ChartRecord =
        ChartRecord(
            songId = id.hashCode(),
            title = id,
            artist = "Artist",
            genre = "maimai",
            bpm = 180,
            songVersion = songVersion,
            songVersionName = if (songVersion == 25500) "舞萌DX 2026" else "Older",
            chartVersion = chartVersion,
            chartVersionName = if (chartVersion == 25500) "舞萌DX 2026" else null,
            songType = SongType.DX,
            difficulty = Difficulty.MASTER,
            levelIndex = Difficulty.MASTER.levelIndex,
            level = "13+",
            levelValue = 13.8,
            noteDesigner = "Designer",
            notes = null,
        )
}

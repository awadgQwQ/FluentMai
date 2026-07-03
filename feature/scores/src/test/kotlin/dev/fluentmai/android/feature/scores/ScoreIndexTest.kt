package dev.fluentmai.android.feature.scores

import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreIndexTest {
    @Test
    fun fallbackMatchingDoesNotCrossSdAndDxCharts() {
        val dxScore = score(songType = SongType.DX)
        val standardChart = chart(songType = SongType.STANDARD, level = "14")
        val dxChart = chart(songType = SongType.DX, level = "12+")

        assertNull(scoreForChartForTest(listOf(dxScore), standardChart))
        assertEquals(dxScore, scoreForChartForTest(listOf(dxScore), dxChart))
    }

    @Test
    fun ratingNewBucketUsesChartVersionInsteadOfSongVersion() {
        val oldSongWithLatestChart = chart(
            songType = SongType.DX,
            level = "13",
            songVersion = 11000,
            chartVersion = 25500,
        )
        val latestSongWithOlderChart = chart(
            songType = SongType.DX,
            level = "13",
            songVersion = 25500,
            chartVersion = 25000,
        )

        assertTrue(oldSongWithLatestChart.isNewRatingBucket(latestChartVersion = 25500))
        assertFalse(latestSongWithOlderChart.isNewRatingBucket(latestChartVersion = 25500))
    }

    private fun score(songType: SongType): ScoreRecord =
        ScoreRecord(
            id = "score-destr0yer-${songType.name.lowercase()}",
            songId = 1051,
            title = "Destr0yer",
            songType = songType,
            difficulty = Difficulty.MASTER,
            level = "12+",
            levelIndex = Difficulty.MASTER.levelIndex,
            achievement = 99.6112,
            dxScore = 1627,
            fc = "fc",
            fs = "sync",
            sourceBatchId = "batch",
            importedAt = 1234L,
        )

    private fun chart(
        songType: SongType,
        level: String,
        songVersion: Int = 20000,
        chartVersion: Int = songVersion,
    ): ChartRecord =
        ChartRecord(
            songId = 1051,
            title = "Destr0yer",
            artist = "Sakujo feat. Nikki Simmons",
            genre = "maimai",
            bpm = 90,
            songVersion = songVersion,
            songVersionName = "DX",
            chartVersion = chartVersion,
            chartVersionName = "DX",
            songType = songType,
            difficulty = Difficulty.MASTER,
            levelIndex = Difficulty.MASTER.levelIndex,
            level = level,
            levelValue = if (songType == SongType.DX) 12.9 else 14.2,
            noteDesigner = "Jack",
            notes = null,
        )
}

package dev.fluentmai.android.feature.scores

import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreIndexTest {
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

    private fun chart(
        songType: SongType,
        level: String,
        songVersion: Int = 20000,
        chartVersion: Int = songVersion,
    ): ChartRecord =
        ChartRecord(
            songId = 424242,
            title = "SYNTHETIC B50 BUCKET SONG",
            artist = "Synthetic Artist",
            genre = "maimai",
            bpm = 180,
            songVersion = songVersion,
            songVersionName = "Synthetic Song Version",
            chartVersion = chartVersion,
            chartVersionName = "Synthetic Chart Version",
            songType = songType,
            difficulty = Difficulty.MASTER,
            levelIndex = Difficulty.MASTER.levelIndex,
            level = level,
            levelValue = 13.2,
            noteDesigner = "Synthetic Designer",
            notes = null,
        )
}

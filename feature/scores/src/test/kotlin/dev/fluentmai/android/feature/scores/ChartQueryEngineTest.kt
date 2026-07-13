package dev.fluentmai.android.feature.scores

import dev.fluentmai.android.core.model.ChartRecord
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.FullComboStatus
import dev.fluentmai.android.core.model.FullSyncStatus
import dev.fluentmai.android.core.model.ScoreRecord
import dev.fluentmai.android.core.model.SongAliasCatalog
import dev.fluentmai.android.core.model.SongAliasEntry
import dev.fluentmai.android.core.model.SongType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChartQueryEngineTest {
    @Test
    fun indexedSearchSupportsFieldsAliasesIdsAndPlayedStatus() {
        val target = chart(
            songId = 834,
            title = "PANDORA PARADOXXX",
            artist = "削除",
            designer = "サファ太",
        )
        val other = chart(songId = 1001, title = "Other", artist = "Artist", designer = "Designer")
        val engine = ChartQueryEngine.create(
            charts = listOf(target, other),
            scores = listOf(scoreFor(target)),
        )

        assertEquals(
            listOf(target),
            engine.query(ChartQueryFilters(searchQuery = "沙发太"), currentVersion = 25_500)
                .items.map { it.chart },
        )
        assertEquals(
            listOf(target),
            engine.query(ChartQueryFilters(searchQuery = "834"), currentVersion = 25_500)
                .items.map { it.chart },
        )
        val played = engine.query(
            ChartQueryFilters(status = ChartStatusFilter.Played),
            currentVersion = 25_500,
        )
        assertEquals(1, played.matchingCount)
        assertEquals(target, played.items.single().chart)
        assertTrue(played.items.single().score != null)
    }

    @Test
    fun currentVersionFilterAndDeterministicSortStayStable() {
        val currentLow = chart(songId = 2, title = "B", levelValue = 13.0, songVersion = 25_500)
        val currentHigh = chart(songId = 1, title = "A", levelValue = 14.0, songVersion = 25_500)
        val future = chart(songId = 3, title = "Future", levelValue = 15.0, songVersion = 25_501)
        val result = ChartQueryEngine.create(listOf(currentLow, future, currentHigh), emptyList()).query(
            filters = ChartQueryFilters(
                version = ChartVersionFilter.Current,
                sort = ChartSort.ConstantDesc,
            ),
            currentVersion = 25_500,
        )

        assertEquals(2, result.matchingCount)
        assertEquals(listOf(currentHigh, currentLow), result.items.map { it.chart })
    }

    @Test
    fun aliasAndCompositeIdentitySearchHandleWidthCaseAndPunctuation() {
        val target = chart(songId = 834, title = "きゅうくらりん")
        val engine = ChartQueryEngine.create(
            charts = listOf(target),
            scores = emptyList(),
            aliases = SongAliasCatalog.from(listOf(SongAliasEntry(834, listOf("心跳不止")))),
        )

        assertEquals(1, engine.query(ChartQueryFilters(searchQuery = " 心 跳・不止！ "), 25_500).matchingCount)
        assertEquals(1, engine.query(ChartQueryFilters(searchQuery = "８３４-dx-master"), 25_500).matchingCount)
    }

    @Test
    fun combinesConstantTypeAchievementComboAndSyncFilters() {
        val matching = chart(songId = 1, title = "Match", levelValue = 13.7)
        val low = chart(songId = 2, title = "Low", levelValue = 12.9)
        val engine = ChartQueryEngine.create(
            listOf(matching, low),
            listOf(scoreFor(matching).copy(achievement = 100.0, fc = "ap", fs = "fsd")),
        )

        val result = engine.query(
            ChartQueryFilters(
                constantMin = 13.5,
                constantMax = 13.9,
                songType = SongType.DX,
                achievementMin = 99.5,
                achievementMax = 100.1,
                fullCombo = FullComboStatus.AP,
                fullSync = FullSyncStatus.FSD,
            ),
            currentVersion = 25_500,
        )

        assertEquals(listOf(matching), result.items.map { it.chart })
    }

    private fun chart(
        songId: Int,
        title: String,
        artist: String = "Artist",
        designer: String = "Designer",
        levelValue: Double = 13.5,
        songVersion: Int = 25_500,
    ): ChartRecord =
        ChartRecord(
            songId = songId,
            title = title,
            artist = artist,
            genre = "maimai",
            bpm = 180,
            songVersion = songVersion,
            songVersionName = if (songVersion == 25_500) "舞萌DX 2026" else null,
            chartVersion = songVersion,
            chartVersionName = if (songVersion == 25_500) "舞萌DX 2026" else null,
            songType = SongType.DX,
            difficulty = Difficulty.MASTER,
            levelIndex = Difficulty.MASTER.levelIndex,
            level = "13+",
            levelValue = levelValue,
            noteDesigner = designer,
            notes = null,
        )

    private fun scoreFor(chart: ChartRecord): ScoreRecord =
        ScoreRecord(
            id = "score-${chart.songId}",
            songId = chart.songId,
            title = chart.title,
            songType = chart.songType,
            difficulty = chart.difficulty,
            level = chart.level,
            levelIndex = chart.levelIndex,
            achievement = 100.5,
            dxScore = 2000,
            fc = "fc",
            fs = "fs",
            sourceBatchId = "batch",
            importedAt = 1L,
        )
}

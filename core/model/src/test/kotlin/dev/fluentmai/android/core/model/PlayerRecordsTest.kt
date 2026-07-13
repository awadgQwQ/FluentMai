package dev.fluentmai.android.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRecordsTest {
    @Test
    fun stableIdentityKeyRoundTripsWithoutRoomIds() {
        val identity = ChartIdentity(834, SongType.DX, Difficulty.MASTER)

        assertEquals(identity, ChartIdentity.parseStableKey(identity.stableKey()))
        assertEquals(null, ChartIdentity.parseStableKey("834:DX:missing"))
    }

    @Test
    fun scoreChartMatchingUsesStableIdAndRejectsAmbiguousTitleFallback() {
        val first = chart(songId = 1, title = "Same")
        val second = chart(songId = 2, title = "Same")
        val direct = score(songId = 2, title = "Same", achievement = 100.0).copy(id = "direct")
        val ambiguous = score(songId = null, title = "Same", achievement = 100.0).copy(id = "ambiguous")

        val matches = matchChartsForScores(listOf(first, second), listOf(direct, ambiguous))

        assertEquals(second, matches["direct"])
        assertFalse(matches.containsKey("ambiguous"))
    }
    @Test
    fun `catalog uses stable identity and best score`() {
        val chart = chart(songId = 123, difficulty = Difficulty.MASTER)
        val low = score(songId = 123, achievement = 99.0)
        val high = score(songId = 123, achievement = 100.5)

        val catalog = buildPlayerRecordCatalog(listOf(chart), listOf(low, high))

        assertEquals(1, catalog.records.size)
        assertEquals(high, catalog.records.single().score)
        assertEquals(ChartIdentity(123, SongType.DX, Difficulty.MASTER), catalog.records.single().identity)
        assertEquals(0, catalog.unmatchedScoreCount)
    }

    @Test
    fun `title fallback handles width punctuation and case`() {
        val chart = chart(songId = 99, title = "Ａ-B C!", difficulty = Difficulty.MASTER)
        val score = score(songId = null, title = "a b-c", achievement = 100.0)

        assertEquals(score, buildPlayerRecordCatalog(listOf(chart), listOf(score)).records.single().score)
    }

    @Test
    fun `ambiguous same-title fallback never reuses one score`() {
        val charts = listOf(
            chart(songId = 1, title = "Same title", difficulty = Difficulty.MASTER),
            chart(songId = 2, title = "Same title", difficulty = Difficulty.MASTER),
        )
        val score = score(songId = null, title = "Same title", achievement = 100.0)

        val catalog = buildPlayerRecordCatalog(charts, listOf(score))

        assertEquals(0, catalog.records.count { it.score != null })
        assertEquals(1, catalog.unmatchedScoreCount)
    }

    @Test
    fun `stats count exact rank combo and sync states`() {
        val charts = listOf(
            chart(1, difficulty = Difficulty.MASTER),
            chart(2, difficulty = Difficulty.MASTER),
            chart(3, difficulty = Difficulty.MASTER),
        )
        val scores = listOf(
            score(1, achievement = 100.5, fc = "app", fs = "fsdp"),
            score(2, achievement = 100.0, fc = "fc", fs = "sync"),
        )

        val stats = buildPlayerRecordCatalog(charts, scores).stats()

        assertEquals(2, stats.playedCharts)
        assertEquals(1, stats.unplayedCharts)
        assertEquals(1, stats.rankCounts[AchievementRank.SSS_PLUS])
        assertEquals(1, stats.rankCounts[AchievementRank.SSS])
        assertEquals(1, stats.fullComboCounts[FullComboStatus.AP_PLUS])
        assertEquals(1, stats.fullSyncCounts[FullSyncStatus.FSD_PLUS])
    }

    @Test
    fun `version plate rules exclude remaster and report blockers`() {
        val records = buildPlayerRecordCatalog(
            charts = listOf(
                chart(1, Difficulty.BASIC, version = 24000),
                chart(1, Difficulty.MASTER, version = 24000),
                chart(1, Difficulty.RE_MASTER, version = 24000),
                chart(2, Difficulty.MASTER, version = 25000),
            ),
            scores = listOf(
                score(1, Difficulty.BASIC, achievement = 100.0, fc = "fc", fs = "fsd"),
                score(1, Difficulty.MASTER, achievement = 99.5, fc = "fc", fs = "fs"),
                score(1, Difficulty.RE_MASTER, achievement = 0.0),
            ),
        ).records

        val general = calculatePlateProgress(records, PlateKind.GENERAL, 24000, "DX 2024")
        val extreme = calculatePlateProgress(records, PlateKind.EXTREME, 24000, "DX 2024")
        val maimai = calculatePlateProgress(records, PlateKind.MAIMAI, 24000, "DX 2024")

        assertEquals(2, general.requiredCount)
        assertEquals(1, general.completedCount)
        assertEquals(2, extreme.completedCount)
        assertEquals(1, maimai.completedCount)
        assertFalse(general.isComplete)
    }

    @Test
    fun `conqueror uses standard basic through remaster and eighty percent clear`() {
        val records = buildPlayerRecordCatalog(
            charts = listOf(
                chart(1, Difficulty.BASIC, songType = SongType.STANDARD),
                chart(1, Difficulty.RE_MASTER, songType = SongType.STANDARD),
                chart(2, Difficulty.MASTER, songType = SongType.DX),
            ),
            scores = listOf(
                score(1, Difficulty.BASIC, songType = SongType.STANDARD, achievement = 80.0),
                score(1, Difficulty.RE_MASTER, songType = SongType.STANDARD, achievement = 79.9999),
                score(2, Difficulty.MASTER, songType = SongType.DX, achievement = 100.5),
            ),
        ).records

        val progress = calculatePlateProgress(records, PlateKind.CONQUEROR, null, null)

        assertEquals(2, progress.requiredCount)
        assertEquals(1, progress.completedCount)
        assertEquals(1, progress.remainingCount)
    }

    @Test
    fun `missing version data never claims completion`() {
        val progress = calculatePlateProgress(emptyList(), PlateKind.GOD, null, null)

        assertFalse(progress.dataSufficient)
        assertFalse(progress.isComplete)
        assertTrue(progress.dataMessage!!.contains("版本"))
    }

    @Test
    fun `record filters compose version constant state type and status`() {
        val catalog = buildPlayerRecordCatalog(
            charts = listOf(
                chart(1, Difficulty.MASTER, version = 24000, songType = SongType.DX),
                chart(2, Difficulty.EXPERT, version = 25000, songType = SongType.STANDARD),
                chart(3, Difficulty.MASTER, version = 25000, songType = SongType.DX),
            ),
            scores = listOf(
                score(1, Difficulty.MASTER, songType = SongType.DX, achievement = 100.5, fc = "app", fs = "fsdp"),
                score(2, Difficulty.EXPERT, songType = SongType.STANDARD, achievement = 99.0, fc = "fc", fs = "sync"),
            ),
        )

        val result = filterPlayerRecords(
            catalog.records,
            PlayerRecordFilters(
                constantMin = 13.0,
                constantMax = 14.0,
                difficulty = Difficulty.MASTER,
                songType = SongType.DX,
                rank = AchievementRank.SSS_PLUS,
                fullCombo = FullComboStatus.AP_PLUS,
                fullSync = FullSyncStatus.FSD_PLUS,
                played = PlayedFilter.PLAYED,
                versionAge = VersionAgeFilter.OLD,
            ),
            currentVersionId = 25000,
        )

        assertEquals(listOf(1), result.map { it.chart.songId })
    }

    @Test
    fun `plate blocker filter uses the selected rule and eligibility`() {
        val catalog = buildPlayerRecordCatalog(
            charts = listOf(
                chart(1, Difficulty.MASTER),
                chart(2, Difficulty.MASTER),
                chart(3, Difficulty.RE_MASTER),
            ),
            scores = listOf(
                score(1, Difficulty.MASTER, achievement = 100.0),
                score(2, Difficulty.MASTER, achievement = 99.9),
            ),
        )

        val blockers = filterPlayerRecords(
            catalog.records,
            PlayerRecordFilters(plateBlockerFor = PlateKind.GENERAL),
            currentVersionId = 25000,
        )

        assertEquals(listOf(2), blockers.map { it.chart.songId })
    }

    @Test
    fun `record sorting has stable chart identity tie breaker`() {
        val records = buildPlayerRecordCatalog(
            charts = listOf(chart(3), chart(1), chart(2)),
            scores = emptyList(),
        ).records

        val sorted = filterPlayerRecords(
            records,
            PlayerRecordFilters(sort = PlayerRecordSort.RATING_DESC),
            currentVersionId = 25000,
        )

        assertEquals(listOf(1, 2, 3), sorted.map { it.chart.songId })
    }

    private fun chart(
        songId: Int,
        difficulty: Difficulty = Difficulty.MASTER,
        version: Int = 24000,
        title: String = "Song $songId",
        songType: SongType = SongType.DX,
    ) = ChartRecord(
        songId = songId,
        title = title,
        artist = "Artist",
        genre = "maimai",
        bpm = 180,
        songVersion = version,
        songVersionName = "Version",
        chartVersion = version,
        chartVersionName = "Version",
        songType = songType,
        difficulty = difficulty,
        levelIndex = difficulty.levelIndex,
        level = "13+",
        levelValue = 13.8,
        noteDesigner = "Designer",
        notes = null,
    )

    private fun score(
        songId: Int?,
        difficulty: Difficulty = Difficulty.MASTER,
        title: String = "Song ${songId ?: 99}",
        songType: SongType = SongType.DX,
        achievement: Double,
        fc: String? = null,
        fs: String? = null,
    ) = ScoreRecord(
        id = "${songId ?: "title"}-${difficulty.name}-$achievement",
        songId = songId,
        title = title,
        songType = songType,
        difficulty = difficulty,
        level = "13+",
        levelIndex = difficulty.levelIndex,
        achievement = achievement,
        dxScore = 2000,
        fc = fc,
        fs = fs,
        sourceBatchId = "batch",
        importedAt = 1L,
    )
}

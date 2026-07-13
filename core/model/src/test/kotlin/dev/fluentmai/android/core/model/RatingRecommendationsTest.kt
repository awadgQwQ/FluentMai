package dev.fluentmai.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RatingRecommendationsTest {
    @Test
    fun targetTotalRatingExplainsB15ReplacementGain() {
        val cutoffRecords = (1..15).map { playerRecord(it, chartVersion = 25_500, constant = 13.0, achievement = 100.5) }
        val candidate = playerRecord(16, chartVersion = 25_500, constant = 13.5, achievement = 97.0)
        val currentTotal = cutoffRecords.sumOf { requireNotNull(it.rating) }

        val result = buildRatingRecommendations(
            records = cutoffRecords + candidate,
            currentVersion = currentVersion(),
            filters = RatingRecommendationFilters(
                targetTotalRating = currentTotal + 5,
                onlyB50Gain = true,
            ),
        )

        val recommendation = result.recommendations.single { it.identity == candidate.identity }
        assertEquals(RatingRecommendationBucket.CURRENT, recommendation.bucket)
        assertEquals(292, recommendation.bucketCutoffRating)
        assertFalse(recommendation.wasInBestSet)
        assertTrue(recommendation.willEnterBestSet)
        assertEquals(RatingRecommendationReason.ENTERS_BEST_SET, recommendation.reason)
        assertTrue(recommendation.actualB50Gain >= 5)
        assertTrue(recommendation.projectedTotalRating >= currentTotal + 5)
        assertTrue(recommendation.targetSingleRating > recommendation.bucketCutoffRating)
    }

    @Test
    fun improvementInsideB15DirectlyRaisesTotal() {
        val candidate = playerRecord(1, chartVersion = 25_500, constant = 13.5, achievement = 100.0)
        val otherRecords = (2..15).map { playerRecord(it, chartVersion = 25_500, constant = 12.0, achievement = 100.5) }

        val result = buildRatingRecommendations(
            records = listOf(candidate) + otherRecords,
            currentVersion = currentVersion(),
            filters = RatingRecommendationFilters(
                targetAchievement = 100.5,
                onlyB50Gain = true,
            ),
        )

        val recommendation = result.recommendations.single { it.identity == candidate.identity }
        assertTrue(recommendation.wasInBestSet)
        assertTrue(recommendation.willEnterBestSet)
        assertEquals(12, recommendation.theoreticalSingleGain)
        assertEquals(12, recommendation.actualB50Gain)
        assertEquals(RatingRecommendationReason.ALREADY_IN_BEST_SET, recommendation.reason)
    }

    @Test
    fun completedUserTargetIsMarkedWithoutInventingGain() {
        val candidate = playerRecord(1, chartVersion = 25_500, constant = 13.5, achievement = 100.0)

        val result = buildRatingRecommendations(
            records = listOf(candidate),
            currentVersion = currentVersion(),
            filters = RatingRecommendationFilters(
                targetAchievement = 99.0,
                excludeSssPlus = false,
                onlyB50Gain = false,
            ),
        )

        val recommendation = result.recommendations.single()
        assertTrue(recommendation.isCompleted)
        assertEquals(99.0, recommendation.targetAchievement, 0.0)
        assertEquals(recommendation.currentSingleRating, recommendation.targetSingleRating)
        assertEquals(0, recommendation.theoreticalSingleGain)
        assertEquals(0, recommendation.actualB50Gain)
        assertEquals(RatingRecommendationReason.TARGET_COMPLETED, recommendation.reason)
    }

    @Test
    fun filtersExcludeUnavailableSssPlusAndUserExcludedCharts() {
        val old = playerRecord(1, chartVersion = 25_000, constant = 13.5, achievement = 99.0)
        val current = playerRecord(2, chartVersion = 25_500, constant = 13.5, achievement = 99.0)
        val sssPlus = playerRecord(3, chartVersion = 25_500, constant = 13.5, achievement = 100.5)
        val disabled = playerRecord(4, chartVersion = 25_500, constant = 13.5, achievement = 99.0, disabled = true)
        val future = playerRecord(5, chartVersion = 25_501, constant = 13.5, achievement = 99.0)

        val result = buildRatingRecommendations(
            records = listOf(old, current, sssPlus, disabled, future),
            currentVersion = currentVersion(),
            filters = RatingRecommendationFilters(
                versionAge = VersionAgeFilter.CURRENT,
                excludedIdentities = setOf(current.identity),
                onlyB50Gain = false,
            ),
        )

        assertEquals(RatingRecommendationAvailability.AVAILABLE, result.availability)
        assertEquals(4, result.eligiblePlayedCharts)
        assertEquals(
            listOf(old, current, sssPlus, disabled).sumOf { requireNotNull(it.rating) },
            result.currentTotalRating,
        )
        assertTrue(result.recommendations.isEmpty())
    }

    @Test
    fun missingVersionFailsClosedAndSameInputIsDeterministic() {
        val records = listOf(
            playerRecord(1, chartVersion = 25_000, constant = 13.4, achievement = 99.5),
            playerRecord(2, chartVersion = 25_500, constant = 13.5, achievement = 99.0),
        )

        val unavailable = buildRatingRecommendations(records, currentVersion = null)
        assertEquals(RatingRecommendationAvailability.CURRENT_VERSION_UNAVAILABLE, unavailable.availability)
        assertTrue(unavailable.recommendations.isEmpty())

        val filters = RatingRecommendationFilters(onlyB50Gain = false)
        val first = buildRatingRecommendations(records, currentVersion(), filters)
        val second = buildRatingRecommendations(records, currentVersion(), filters)
        assertEquals(first, second)
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidConstantRangeIsRejected() {
        buildRatingRecommendations(
            records = listOf(playerRecord(1, chartVersion = 25_500, constant = 13.5, achievement = 99.0)),
            currentVersion = currentVersion(),
            filters = RatingRecommendationFilters(constantMin = 14.0, constantMax = 13.0),
        )
    }

    private fun currentVersion(): MaimaiCurrentVersion =
        MaimaiCurrentVersion(
            majorVersion = MaimaiMajorVersion(25_500, "舞萌DX 2026"),
            source = MaimaiCurrentVersionSource.CATALOG_VERSION_TABLE,
        )

    private fun playerRecord(
        id: Int,
        chartVersion: Int,
        constant: Double,
        achievement: Double,
        disabled: Boolean = false,
    ): PlayerChartRecord {
        val chart = ChartRecord(
            songId = id,
            title = "Song $id",
            artist = "Artist",
            genre = "maimai",
            bpm = 180,
            songVersion = chartVersion,
            songVersionName = null,
            chartVersion = chartVersion,
            chartVersionName = null,
            songType = SongType.DX,
            difficulty = Difficulty.MASTER,
            levelIndex = Difficulty.MASTER.levelIndex,
            level = "13+",
            levelValue = constant,
            noteDesigner = "Designer",
            notes = null,
            isLocked = false,
            isDisabled = disabled,
        )
        val score = ScoreRecord(
            id = "score-$id",
            songId = id,
            title = chart.title,
            songType = chart.songType,
            difficulty = chart.difficulty,
            level = chart.level,
            levelIndex = chart.levelIndex,
            achievement = achievement,
            dxScore = null,
            fc = null,
            fs = null,
            sourceBatchId = "batch",
            importedAt = 1L,
        )
        return PlayerChartRecord(
            identity = ChartIdentity.from(chart),
            chart = chart,
            score = score,
            rating = calculateDxRating(constant, achievement),
        )
    }
}

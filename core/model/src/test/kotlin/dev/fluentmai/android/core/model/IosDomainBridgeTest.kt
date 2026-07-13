package dev.fluentmai.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IosDomainBridgeTest {
    @Test
    fun `rating analyzer applies B35 and B15 buckets`() {
        val analyzer = IosRatingAnalyzer(currentVersionId = 24000)
        repeat(20) { index ->
            analyzer.addScore("new-$index", 14.0, 100.5 - index / 100.0, 24000)
        }
        repeat(40) { index ->
            analyzer.addScore("old-$index", 13.0, 100.5 - index / 100.0, 23000)
        }
        analyzer.addScore("future", 15.0, 100.5, 25000)

        val result = analyzer.build()

        assertEquals(15, result.newBest.size)
        assertEquals(35, result.oldBest.size)
        assertEquals(1, result.ineligibleCount)
        assertEquals(10, result.outsideBestCount)
        assertEquals(result.newBest.sumOf { it.rating } + result.oldBest.sumOf { it.rating }, result.totalRating)
        assertTrue(result.newBest.first().rating >= result.newBest.last().rating)
    }

    @Test
    fun `achievement bridge accepts stable enum names`() {
        val result = IosDomainBridge().calculateAchievement(
            tap = 400,
            hold = 50,
            slide = 50,
            touch = 30,
            breakCount = 20,
            noteKind = "BREAK",
            judgement = "GREAT",
            occurrences = 1,
            targetAchievement = 100.0,
        )

        assertTrue(result.resultingAchievement < result.maximumAchievement)
        assertTrue(result.lossPerJudgement > 0.0)
    }

    @Test
    fun `achievement bridge rejects unknown enum values`() {
        assertFailsWith<IllegalArgumentException> {
            IosDomainBridge().calculateAchievement(1, 0, 0, 0, 0, "UNKNOWN", "MISS", 0, 0.0)
        }
    }
}

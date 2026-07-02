package dev.fluentmai.android.feature.scores

import org.junit.Assert.assertEquals
import org.junit.Test

class DxRatingTest {
    @Test
    fun capsAchievementAt100Point5ForSssPlus() {
        assertEquals(303, calculateDxRating(levelValue = 13.5, achievement = 100.6217))
        assertEquals(303, calculateDxRating(levelValue = 13.5, achievement = 100.5))
    }

    @Test
    fun appliesUpperBoundaryCoefficients() {
        assertEquals(332, calculateDxRating(levelValue = 14.9, achievement = 100.4999))
        assertEquals(323, calculateDxRating(levelValue = 14.9, achievement = 100.4567))
        assertEquals(318, calculateDxRating(levelValue = 14.9, achievement = 99.9999))
        assertEquals(314, calculateDxRating(levelValue = 14.9, achievement = 99.8765))
        assertEquals(303, calculateDxRating(levelValue = 14.9, achievement = 98.9999))
        assertEquals(299, calculateDxRating(levelValue = 14.9, achievement = 98.8765))
        assertEquals(254, calculateDxRating(levelValue = 14.9, achievement = 96.9999))
        assertEquals(242, calculateDxRating(levelValue = 14.9, achievement = 96.8765))
        assertEquals(152, calculateDxRating(levelValue = 14.9, achievement = 79.9999))
        assertEquals(142, calculateDxRating(levelValue = 14.9, achievement = 79.8765))
    }

    @Test
    fun doesNotAddAllPerfectBonusForCurrentDivingFishRules() {
        assertEquals(303, calculateDxRating(levelValue = 13.5, achievement = 100.6217, comboFlag = "ap"))
        assertEquals(303, calculateDxRating(levelValue = 13.5, achievement = 100.6217, comboFlag = "APP"))
        assertEquals(303, calculateDxRating(levelValue = 13.5, achievement = 100.6217, comboFlag = "fcp"))
        assertEquals(288, calculateDxRating(levelValue = 12.8, achievement = 100.9754, comboFlag = "ap"))
        assertEquals(281, calculateDxRating(levelValue = 12.5, achievement = 100.9826, comboFlag = "ap"))
    }

    @Test
    fun coefficientTableMatchesDocumentedThresholds() {
        assertEquals(22.4, dxRatingCoefficient(100.5), 0.0)
        assertEquals(22.2, dxRatingCoefficient(100.4999), 0.0)
        assertEquals(21.6, dxRatingCoefficient(100.0), 0.0)
        assertEquals(21.4, dxRatingCoefficient(99.9999), 0.0)
        assertEquals(21.1, dxRatingCoefficient(99.5), 0.0)
        assertEquals(20.8, dxRatingCoefficient(99.0), 0.0)
        assertEquals(20.6, dxRatingCoefficient(98.9999), 0.0)
        assertEquals(20.3, dxRatingCoefficient(98.0), 0.0)
        assertEquals(20.0, dxRatingCoefficient(97.0), 0.0)
        assertEquals(17.6, dxRatingCoefficient(96.9999), 0.0)
        assertEquals(16.8, dxRatingCoefficient(94.0), 0.0)
        assertEquals(15.2, dxRatingCoefficient(90.0), 0.0)
        assertEquals(13.6, dxRatingCoefficient(80.0), 0.0)
        assertEquals(12.8, dxRatingCoefficient(79.9999), 0.0)
        assertEquals(12.0, dxRatingCoefficient(75.0), 0.0)
        assertEquals(11.2, dxRatingCoefficient(70.0), 0.0)
        assertEquals(9.6, dxRatingCoefficient(60.0), 0.0)
        assertEquals(8.0, dxRatingCoefficient(50.0), 0.0)
        assertEquals(6.4, dxRatingCoefficient(40.0), 0.0)
        assertEquals(4.8, dxRatingCoefficient(30.0), 0.0)
        assertEquals(3.2, dxRatingCoefficient(20.0), 0.0)
        assertEquals(1.6, dxRatingCoefficient(10.0), 0.0)
        assertEquals(0.0, dxRatingCoefficient(9.9999), 0.0)
    }
}

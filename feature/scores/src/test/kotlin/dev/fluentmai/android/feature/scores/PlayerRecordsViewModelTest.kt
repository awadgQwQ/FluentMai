package dev.fluentmai.android.feature.scores

import androidx.lifecycle.SavedStateHandle
import dev.fluentmai.android.core.model.ChartIdentity
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.SongType
import dev.fluentmai.android.core.model.VersionAgeFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerRecordsViewModelTest {
    @Test
    fun recommendationSectionFiltersAndExclusionsRestoreFromSavedState() {
        val handle = SavedStateHandle()
        val identity = ChartIdentity(834, SongType.DX, Difficulty.MASTER)
        val original = PlayerRecordsViewModel(handle)

        original.updateSection(PlayerRecordsSection.RECOMMENDATIONS)
        original.updateRecommendationTargetTotal("14700")
        original.updateRecommendationTargetAchievement("100.5")
        original.updateRecommendationConstantMin("13.2")
        original.updateRecommendationConstantMax("14.7")
        original.updateRecommendationVersionAge(VersionAgeFilter.CURRENT)
        original.updateRecommendationExcludeSssPlus(false)
        original.updateRecommendationOnlyB50Gain(false)
        original.excludeRecommendation(identity)

        val restored = PlayerRecordsViewModel(handle).uiState.value
        assertEquals(PlayerRecordsSection.RECOMMENDATIONS, restored.section)
        assertEquals("14700", restored.recommendationTargetTotalText)
        assertEquals("100.5", restored.recommendationTargetAchievementText)
        assertEquals("13.2", restored.recommendationConstantMinText)
        assertEquals("14.7", restored.recommendationConstantMaxText)
        assertEquals(VersionAgeFilter.CURRENT, restored.recommendationFilters.versionAge)
        assertFalse(restored.recommendationFilters.excludeSssPlus)
        assertFalse(restored.recommendationFilters.onlyB50Gain)
        assertTrue(identity in restored.recommendationFilters.excludedIdentities)
    }
}

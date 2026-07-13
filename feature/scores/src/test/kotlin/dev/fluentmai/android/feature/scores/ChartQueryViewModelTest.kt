package dev.fluentmai.android.feature.scores

import androidx.lifecycle.SavedStateHandle
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.model.FullComboStatus
import dev.fluentmai.android.core.model.FullSyncStatus
import dev.fluentmai.android.core.model.SongType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChartQueryViewModelTest {
    @Test
    fun filtersSortAndScrollRestoreFromSavedState() {
        val handle = SavedStateHandle()
        val original = ChartQueryViewModel(handle)

        original.updateSearchQuery("PANDORA")
        original.updateLevelQuery("13+")
        original.updateConstantRange(13.2, 14.7)
        original.updateDifficulty(Difficulty.MASTER)
        original.updateGenre(ChartGenreFilter.Maimai)
        original.updateVersion(ChartVersionFilter.Current)
        original.updateStatus(ChartStatusFilter.Played)
        original.updateSongType(SongType.DX)
        original.updateAchievementRange(99.5, 100.5)
        original.updateFullCombo(FullComboStatus.AP)
        original.updateFullSync(FullSyncStatus.FSD)
        original.updateSort(ChartSort.AchievementDesc)
        original.saveScroll(index = 17, offset = 96)

        val restored = ChartQueryViewModel(handle)
        assertEquals("PANDORA", restored.uiState.value.filters.searchQuery)
        assertEquals("13+", restored.uiState.value.filters.levelQuery)
        assertEquals(13.2, restored.uiState.value.filters.constantMin)
        assertEquals(14.7, restored.uiState.value.filters.constantMax)
        assertEquals(Difficulty.MASTER, restored.uiState.value.filters.difficulty)
        assertEquals(ChartGenreFilter.Maimai, restored.uiState.value.filters.genre)
        assertEquals(ChartVersionFilter.Current, restored.uiState.value.filters.version)
        assertEquals(ChartStatusFilter.Played, restored.uiState.value.filters.status)
        assertEquals(SongType.DX, restored.uiState.value.filters.songType)
        assertEquals(99.5, restored.uiState.value.filters.achievementMin)
        assertEquals(100.5, restored.uiState.value.filters.achievementMax)
        assertEquals(FullComboStatus.AP, restored.uiState.value.filters.fullCombo)
        assertEquals(FullSyncStatus.FSD, restored.uiState.value.filters.fullSync)
        assertEquals(ChartSort.AchievementDesc, restored.uiState.value.filters.sort)
        assertEquals(17, restored.restoredScrollIndex)
        assertEquals(96, restored.restoredScrollOffset)
    }

    @Test
    fun invalidSavedEnumValuesFailClosedToDefaults() {
        val restored = ChartQueryViewModel(
            SavedStateHandle(
                mapOf(
                    "charts.difficulty" to "UNKNOWN",
                    "charts.genre" to "UNKNOWN",
                    "charts.version" to "UNKNOWN",
                    "charts.status" to "UNKNOWN",
                    "charts.sort" to "UNKNOWN",
                ),
            ),
        )

        assertNull(restored.uiState.value.filters.difficulty)
        assertEquals(ChartGenreFilter.All, restored.uiState.value.filters.genre)
        assertEquals(ChartVersionFilter.All, restored.uiState.value.filters.version)
        assertEquals(ChartStatusFilter.All, restored.uiState.value.filters.status)
        assertEquals(ChartSort.ConstantDesc, restored.uiState.value.filters.sort)
    }
}

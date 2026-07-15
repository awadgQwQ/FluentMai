package dev.fluentmai.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationTest {
    @Test
    fun productNavigationHasExactlyFourConvergedDestinations() {
        assertEquals(listOf("首页", "导入", "谱面", "工具"), AppTab.entries.map { it.label })
    }

    @Test
    fun phoneWidthUsesBottomNavigation() {
        assertFalse(usesNavigationRail(599f))
    }

    @Test
    fun expandedWidthsUseNavigationRail() {
        assertTrue(usesNavigationRail(600f))
        assertTrue(usesNavigationRail(840f))
    }
}

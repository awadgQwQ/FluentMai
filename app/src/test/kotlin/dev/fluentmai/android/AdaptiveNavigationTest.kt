package dev.fluentmai.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveNavigationTest {
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

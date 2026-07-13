package dev.fluentmai.android.feature.importflow

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportScreenTest {
    @Test
    fun rebuildConfirmationRequiresExactPhrase() {
        assertFalse(isDivingFishRebuildConfirmationAccepted(""))
        assertFalse(isDivingFishRebuildConfirmationAccepted("确认清空云端"))
        assertFalse(isDivingFishRebuildConfirmationAccepted("我确认清空水鱼"))
        assertTrue(isDivingFishRebuildConfirmationAccepted("  $DIVING_FISH_REBUILD_CONFIRMATION_PHRASE  "))
    }
}

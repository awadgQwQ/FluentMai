package dev.fluentmai.android

import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCatalogRefreshGuardTest {
    private val existing = SongCatalogMetrics(
        songCount = 1_300,
        chartCount = 5_360,
        majorVersionCount = 20,
        latestMajorVersion = 25_500,
    )

    @Test
    fun acceptsCompleteCatalogGrowth() {
        assertNull(
            catalogRefreshRejectionReason(
                incoming = existing.copy(songCount = 1_305, chartCount = 5_390),
                existing = existing,
            ),
        )
    }

    @Test
    fun rejectsEmptyOrStructurallyIncompleteResponse() {
        assertTrue(
            catalogRefreshRejectionReason(existing.copy(songCount = 0), existing)
                ?.contains("no songs") == true,
        )
        assertTrue(
            catalogRefreshRejectionReason(existing.copy(majorVersionCount = 0), existing)
                ?.contains("major-version") == true,
        )
    }

    @Test
    fun rejectsPartialResponseThatWouldReplaceValidCache() {
        assertTrue(
            catalogRefreshRejectionReason(
                incoming = existing.copy(songCount = 500, chartCount = 2_000),
                existing = existing,
            )?.contains("regressed") == true,
        )
    }

    @Test
    fun rejectsMajorVersionRegression() {
        assertTrue(
            catalogRefreshRejectionReason(
                incoming = existing.copy(latestMajorVersion = 25_000),
                existing = existing,
            )?.contains("major version regressed") == true,
        )
    }
}

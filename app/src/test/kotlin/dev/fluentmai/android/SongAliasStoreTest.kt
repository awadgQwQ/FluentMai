package dev.fluentmai.android

import dev.fluentmai.android.core.privacy.PrivacyRedactor
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongAliasStoreTest {
    @Test
    fun parsesAndMergesAliasPayloadBySongId() {
        val catalog = parseLxnsAliasCatalog(
            """{
              "aliases": [
                {"song_id": 10, "aliases": ["Alias", " 同名 "]},
                {"song_id": 10, "aliases": ["alias", "Another"]},
                {"song_id": 11, "aliases": ["Eleven"]}
              ]
            }""",
        )

        assertEquals(2, catalog.songCount)
        assertEquals(listOf("Alias", "Another", "同名"), catalog.aliasesFor(10))
    }

    @Test
    fun rejectsEmptyAndTruncatedRefreshesButAcceptsGrowth() {
        val existing = AliasCatalogMetrics(songCount = 1_000, aliasCount = 2_500)

        assertTrue(aliasRefreshRejectionReason(AliasCatalogMetrics(0, 0), existing)!!.contains("empty"))
        assertTrue(aliasRefreshRejectionReason(AliasCatalogMetrics(500, 1_000), existing)!!.contains("regressed"))
        assertNull(aliasRefreshRejectionReason(AliasCatalogMetrics(1_010, 2_550), existing))
    }

    @Test
    fun yuzuAliasesNormalizeDxIdsButKeepUtageIdentity() {
        val catalog = parseYuzuAliasCatalog(
            """[
              {"song_id": 1512, "name": "SD", "alias": ["心跳不止"]},
              {"song_id": 11512, "name": "DX", "alias": ["Duplicate", "心跳不止"]},
              {"song_id": 100227, "name": "Utage", "alias": ["宴会别名"]}
            ]""",
        )

        assertEquals(listOf("Duplicate", "心跳不止"), catalog.aliasesFor(1512))
        assertEquals(listOf("宴会别名"), catalog.aliasesFor(100227))
    }

    @Test
    fun failedRefreshRetainsPreviouslyValidatedCache() {
        val directory = Files.createTempDirectory("fluentmai-alias-test").toFile()
        var response = aliasPayload(songCount = 10, aliasesPerSong = 2)
        val store = SongAliasStore(
            cacheFile = directory.resolve("lxns-maimai-alias-cache-v1.json"),
            fetchAliasJson = { response },
            fetchYuzuAliasJson = { "[]" },
            redactor = PrivacyRedactor(),
            clock = { 1234L },
        )
        try {
            val initial = store.refreshFromNetwork((1..10).toSet())
            response = """{"aliases": []}"""

            runCatching { store.refreshFromNetwork((1..10).toSet()) }
            val retained = store.loadLocalCatalog((1..10).toSet())

            assertEquals(initial.contentVersion, retained?.contentVersion)
            assertEquals(10, retained?.songCount)
            assertEquals(20, retained?.aliasCount)
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun aliasPayload(songCount: Int, aliasesPerSong: Int): String =
        buildString {
            append("{\"aliases\":[")
            (1..songCount).forEachIndexed { index, songId ->
                if (index > 0) append(',')
                append("{\"song_id\":").append(songId).append(",\"aliases\":[")
                repeat(aliasesPerSong) { aliasIndex ->
                    if (aliasIndex > 0) append(',')
                    append('"').append("Alias-").append(songId).append('-').append(aliasIndex).append('"')
                }
                append("]}")
            }
            append("]}")
        }
}

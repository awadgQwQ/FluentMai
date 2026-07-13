package dev.fluentmai.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class SongAliasesTest {
    @Test
    fun `catalog merges duplicate ids and aliases deterministically`() {
        val catalog = SongAliasCatalog.from(
            listOf(
                SongAliasEntry(2, listOf("Second", "same")),
                SongAliasEntry(1, listOf(" First ", "SAME")),
                SongAliasEntry(2, listOf("Same", "Another")),
                SongAliasEntry(-1, listOf("ignored")),
            ),
        )

        assertEquals(listOf(1, 2), catalog.entries.map { it.songId })
        assertEquals(listOf("Another", "same", "Second"), catalog.aliasesFor(2))
        assertEquals(5, catalog.aliasCount)
    }
}

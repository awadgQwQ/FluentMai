package dev.fluentmai.android.core.model

data class SongAliasEntry(
    val songId: Int,
    val aliases: List<String>,
)

class SongAliasCatalog private constructor(
    val entries: List<SongAliasEntry>,
) {
    private val aliasesBySongId = entries.associate { it.songId to it.aliases }

    val songCount: Int = entries.size
    val aliasCount: Int = entries.sumOf { it.aliases.size }

    fun aliasesFor(songId: Int): List<String> = aliasesBySongId[songId].orEmpty()

    companion object {
        val Empty = SongAliasCatalog(emptyList())

        fun from(entries: List<SongAliasEntry>): SongAliasCatalog {
            val merged = entries
                .asSequence()
                .filter { it.songId > 0 }
                .groupBy { it.songId }
                .map { (songId, matchingEntries) ->
                    SongAliasEntry(
                        songId = songId,
                        aliases = matchingEntries
                            .flatMap { it.aliases }
                            .map(String::trim)
                            .filter(String::isNotEmpty)
                            .distinctBy { it.lowercase() }
                            .sortedWith(String.CASE_INSENSITIVE_ORDER),
                    )
                }
                .filter { it.aliases.isNotEmpty() }
                .sortedBy { it.songId }
            return SongAliasCatalog(merged)
        }
    }
}

package dev.fluentmai.android

import android.content.Context
import dev.fluentmai.android.core.model.SongAliasCatalog
import dev.fluentmai.android.core.model.SongAliasEntry
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

class SongAliasStore internal constructor(
    private val cacheFile: File,
    private val fetchAliasJson: () -> String,
    private val fetchYuzuAliasJson: () -> String = { "[]" },
    private val redactor: PrivacyRedactor,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    constructor(
        context: Context,
        client: LxnsMaimaiSongCatalogClient,
        redactor: PrivacyRedactor,
        clock: () -> Long = System::currentTimeMillis,
    ) : this(
        cacheFile = File(context.applicationContext.filesDir, CACHE_FILE_NAME),
        fetchAliasJson = client::fetchAliasJson,
        fetchYuzuAliasJson = client::fetchYuzuAliasJson,
        redactor = redactor,
        clock = clock,
    )

    fun loadLocalCatalog(knownSongIds: Set<Int>): SongAliasSnapshot? =
        if (!cacheFile.isFile) {
            null
        } else {
            runCatching { parseCache(cacheFile.readText(Charsets.UTF_8), knownSongIds, SongAliasSource.FileCache) }
                .getOrNull()
        }

    fun refreshFromNetwork(knownSongIds: Set<Int>): SongAliasSnapshot {
        val existing = loadLocalCatalog(knownSongIds)
        val sourceCatalogs = listOf(
            "LXNS" to runCatching { parseLxnsAliasCatalog(fetchAliasJson()) },
            "Yuzu" to runCatching { parseYuzuAliasCatalog(fetchYuzuAliasJson()) },
        )
        val usableCatalogs = sourceCatalogs.mapNotNull { (_, result) ->
            result.getOrNull()?.takeIf { it.songCount > 0 && it.aliasCount > 0 }
        }
        if (usableCatalogs.isEmpty()) {
            val failures = sourceCatalogs.joinToString { (name, result) ->
                val message = result.exceptionOrNull()?.let { error ->
                    redactor.redact(error.message ?: error::class.java.simpleName)
                } ?: "empty"
                "$name=$message"
            }
            throw IOException("Unable to obtain a usable community alias catalog: $failures")
        }
        val catalog = SongAliasCatalog.from(usableCatalogs.flatMap { it.entries })
        aliasRefreshRejectionReason(
            incoming = AliasCatalogMetrics(catalog.songCount, catalog.aliasCount),
            existing = existing?.let { AliasCatalogMetrics(it.songCount, it.aliasCount) },
        )?.let { reason -> throw IOException("Refusing unsafe alias refresh: $reason") }

        val fetchedAt = clock()
        val contentVersion = catalog.contentVersion()
        writeCacheAtomically(createCacheJson(catalog, fetchedAt, contentVersion))
        return catalog.toSnapshot(
            knownSongIds = knownSongIds,
            source = SongAliasSource.Network,
            fetchedAtEpochMillis = fetchedAt,
            contentVersion = contentVersion,
        )
    }

    private fun parseCache(
        json: String,
        knownSongIds: Set<Int>,
        source: SongAliasSource,
    ): SongAliasSnapshot {
        val root = JSONObject(json)
        require(root.optInt("schema_version") == CACHE_SCHEMA_VERSION) { "unsupported alias cache schema" }
        val catalog = parseAliasEntries(root.optJSONArray("entries"))
        require(catalog.songCount > 0 && catalog.aliasCount > 0) { "alias cache is empty" }
        val calculatedVersion = catalog.contentVersion()
        val storedVersion = root.optString("content_version")
        require(storedVersion == calculatedVersion) { "alias cache content version mismatch" }
        return catalog.toSnapshot(
            knownSongIds = knownSongIds,
            source = source,
            fetchedAtEpochMillis = root.optLong("fetched_at_epoch_millis", 0L),
            contentVersion = calculatedVersion,
        )
    }

    private fun createCacheJson(
        catalog: SongAliasCatalog,
        fetchedAtEpochMillis: Long,
        contentVersion: String,
    ): String = JSONObject()
        .put("schema_version", CACHE_SCHEMA_VERSION)
        .put("source", ALIAS_SOURCE_URL)
        .put("fetched_at_epoch_millis", fetchedAtEpochMillis)
        .put("content_version", contentVersion)
        .put("entries", catalog.toJsonEntries())
        .toString()

    private fun writeCacheAtomically(json: String) {
        val temporaryFile = File(cacheFile.parentFile, "$CACHE_FILE_NAME.tmp")
        temporaryFile.writeText(json, Charsets.UTF_8)
        try {
            Files.move(
                temporaryFile.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(
                temporaryFile.toPath(),
                cacheFile.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        }
    }

    private companion object {
        private const val CACHE_SCHEMA_VERSION = 1
        private const val CACHE_FILE_NAME = "lxns-maimai-alias-cache-v1.json"
        private const val ALIAS_SOURCE_URL =
            "LXNS:https://maimai.lxns.net/api/v0/maimai/alias/list;" +
                "Yuzu:https://www.yuzuchan.moe/api/v2/aliases/maimaidx/aliases"
    }
}

internal fun parseLxnsAliasCatalog(json: String): SongAliasCatalog {
    val root = JSONObject(json)
    return parseAliasEntries(root.optJSONArray("aliases"))
}

internal fun parseYuzuAliasCatalog(json: String): SongAliasCatalog {
    val entries = JSONArray(json)
    val parsed = mutableListOf<SongAliasEntry>()
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        val sourceSongId = entry.optInt("song_id", -1)
        val songId = when (sourceSongId) {
            in 10_000 until 100_000 -> sourceSongId % 10_000
            else -> sourceSongId
        }
        val aliases = entry.optJSONArray("alias") ?: continue
        val values = buildList {
            for (aliasIndex in 0 until aliases.length()) {
                aliases.optString(aliasIndex).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        parsed += SongAliasEntry(songId, values)
    }
    return SongAliasCatalog.from(parsed)
}

private fun parseAliasEntries(entries: JSONArray?): SongAliasCatalog {
    if (entries == null) return SongAliasCatalog.Empty
    val parsed = mutableListOf<SongAliasEntry>()
    for (index in 0 until entries.length()) {
        val entry = entries.optJSONObject(index) ?: continue
        val songId = entry.optInt("song_id", -1)
        val aliases = entry.optJSONArray("aliases") ?: continue
        val values = buildList {
            for (aliasIndex in 0 until aliases.length()) {
                aliases.optString(aliasIndex).trim().takeIf(String::isNotEmpty)?.let(::add)
            }
        }
        parsed += SongAliasEntry(songId, values)
    }
    return SongAliasCatalog.from(parsed)
}

private fun SongAliasCatalog.toJsonEntries(): JSONArray = JSONArray().apply {
    entries.forEach { entry ->
        put(
            JSONObject()
                .put("song_id", entry.songId)
                .put("aliases", JSONArray(entry.aliases)),
        )
    }
}

private fun SongAliasCatalog.contentVersion(): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.forEach { entry ->
        digest.update(entry.songId.toString().toByteArray(Charsets.UTF_8))
        digest.update(0.toByte())
        entry.aliases.forEach { alias ->
            digest.update(alias.toByteArray(Charsets.UTF_8))
            digest.update(0.toByte())
        }
        digest.update('\n'.code.toByte())
    }
    return "sha256:" + digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}

private fun SongAliasCatalog.toSnapshot(
    knownSongIds: Set<Int>,
    source: SongAliasSource,
    fetchedAtEpochMillis: Long,
    contentVersion: String,
): SongAliasSnapshot {
    val unmapped = entries.map { it.songId }.filterNot(knownSongIds::contains)
    return SongAliasSnapshot(
        catalog = this,
        source = source,
        fetchedAtEpochMillis = fetchedAtEpochMillis,
        contentVersion = contentVersion,
        unmappedSongIds = unmapped,
    )
}

data class SongAliasSnapshot(
    val catalog: SongAliasCatalog,
    val source: SongAliasSource,
    val fetchedAtEpochMillis: Long,
    val contentVersion: String,
    val unmappedSongIds: List<Int>,
) {
    val songCount: Int = catalog.songCount
    val aliasCount: Int = catalog.aliasCount
    val mappedSongCount: Int = songCount - unmappedSongIds.size
}

enum class SongAliasSource(val logName: String) {
    FileCache("file-cache"),
    Network("network"),
}

internal data class AliasCatalogMetrics(
    val songCount: Int,
    val aliasCount: Int,
)

internal fun aliasRefreshRejectionReason(
    incoming: AliasCatalogMetrics,
    existing: AliasCatalogMetrics?,
): String? {
    if (incoming.songCount <= 0 || incoming.aliasCount <= 0) return "incoming alias catalog is empty"
    if (existing == null) return null
    if (incoming.songCount < existing.songCount.retainedAliasMinimum()) {
        return "aliased song count regressed from ${existing.songCount} to ${incoming.songCount}"
    }
    if (incoming.aliasCount < existing.aliasCount.retainedAliasMinimum()) {
        return "alias count regressed from ${existing.aliasCount} to ${incoming.aliasCount}"
    }
    return null
}

private fun Int.retainedAliasMinimum(): Int = ((toLong() * 80L) + 99L).div(100L).toInt()

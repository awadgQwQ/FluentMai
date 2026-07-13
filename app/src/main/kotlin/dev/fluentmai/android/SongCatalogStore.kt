package dev.fluentmai.android

import android.content.Context
import dev.fluentmai.android.core.importer.MaimaiSongCatalog
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class SongCatalogStore(
    context: Context,
    private val client: LxnsMaimaiSongCatalogClient,
    private val redactor: PrivacyRedactor,
) {
    private val appContext = context.applicationContext
    private val cacheFile = File(appContext.filesDir, CACHE_FILE_NAME)

    fun loadLocalCatalog(): SongCatalogSnapshot? {
        if (cacheFile.isFile) {
            runCatching {
                val json = cacheFile.readText(Charsets.UTF_8)
                return parseSnapshot(json, SongCatalogSource.FileCache)
            }
        }
        return runCatching {
            appContext.assets.open(FALLBACK_ASSET_NAME).bufferedReader(Charsets.UTF_8).use { reader ->
                parseSnapshot(reader.readText(), SongCatalogSource.BundledFallback)
            }
        }.getOrNull()
    }

    fun refreshFromNetwork(): SongCatalogSnapshot {
        val existingSnapshot = loadLocalCatalog()
        val json = client.fetchCatalogJson()
        val snapshot = parseSnapshot(json, SongCatalogSource.Network)
        catalogRefreshRejectionReason(
            incoming = snapshot.metrics(),
            existing = existingSnapshot?.metrics(),
        )?.let { reason ->
            throw IOException("Refusing unsafe song catalog refresh: $reason")
        }
        writeCacheAtomically(json)
        return snapshot
    }

    private fun parseSnapshot(
        json: String,
        source: SongCatalogSource,
    ): SongCatalogSnapshot =
        try {
            val catalog = MaimaiSongCatalog.fromLxnsSongListJson(json)
            val snapshot = SongCatalogSnapshot(
                catalog = catalog,
                source = source,
                jsonBytes = json.toByteArray(Charsets.UTF_8).size,
            )
            require(snapshot.songCount > 0) { "catalog contains no songs" }
            require(snapshot.chartCount > 0) { "catalog contains no playable charts" }
            require(snapshot.majorVersionCount > 0) { "catalog contains no major-version table" }
            snapshot
        } catch (error: Exception) {
            throw IOException(
                "Unable to parse ${source.logName} song catalog: ${
                    redactor.redact(error.message ?: error::class.java.simpleName)
                }",
                error,
            )
        }

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
        private const val CACHE_FILE_NAME = "lxns-maimai-song-list-cache.json"
        private const val FALLBACK_ASSET_NAME = "lxns_song_list_fallback.json"
    }
}

data class SongCatalogSnapshot(
    val catalog: MaimaiSongCatalog,
    val source: SongCatalogSource,
    val jsonBytes: Int,
) {
    val songCount: Int = catalog.songCount()
    val chartCount: Int = catalog.charts().size
    val majorVersionCount: Int = catalog.majorVersions().size
    val latestMajorVersion: Int? = catalog.majorVersions().maxOfOrNull { it.id }
}

internal data class SongCatalogMetrics(
    val songCount: Int,
    val chartCount: Int,
    val majorVersionCount: Int,
    val latestMajorVersion: Int?,
)

internal fun SongCatalogSnapshot.metrics(): SongCatalogMetrics =
    SongCatalogMetrics(
        songCount = songCount,
        chartCount = chartCount,
        majorVersionCount = majorVersionCount,
        latestMajorVersion = latestMajorVersion,
    )

internal fun catalogRefreshRejectionReason(
    incoming: SongCatalogMetrics,
    existing: SongCatalogMetrics?,
): String? {
    if (incoming.songCount <= 0) return "incoming catalog has no songs"
    if (incoming.chartCount <= 0) return "incoming catalog has no charts"
    if (incoming.majorVersionCount <= 0 || incoming.latestMajorVersion == null) {
        return "incoming catalog has no major-version metadata"
    }
    if (existing == null) return null
    if (incoming.songCount < existing.songCount.retainedMinimum()) {
        return "song count regressed from ${existing.songCount} to ${incoming.songCount}"
    }
    if (incoming.chartCount < existing.chartCount.retainedMinimum()) {
        return "chart count regressed from ${existing.chartCount} to ${incoming.chartCount}"
    }
    val existingVersion = existing.latestMajorVersion
    if (existingVersion != null && incoming.latestMajorVersion < existingVersion) {
        return "major version regressed from $existingVersion to ${incoming.latestMajorVersion}"
    }
    return null
}

private fun Int.retainedMinimum(): Int =
    ((this.toLong() * MINIMUM_RETAINED_PERCENT) + 99L).div(100L).toInt()

private const val MINIMUM_RETAINED_PERCENT = 80

enum class SongCatalogSource(
    val logName: String,
) {
    FileCache("file-cache"),
    BundledFallback("bundled-fallback"),
    Network("network"),
}

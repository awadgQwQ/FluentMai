package dev.fluentmai.android

import android.content.Context
import dev.fluentmai.android.core.importer.MaimaiSongCatalog
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import java.io.File
import java.io.IOException

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
        val json = client.fetchCatalogJson()
        val snapshot = parseSnapshot(json, SongCatalogSource.Network)
        cacheFile.writeText(json, Charsets.UTF_8)
        return snapshot
    }

    private fun parseSnapshot(
        json: String,
        source: SongCatalogSource,
    ): SongCatalogSnapshot =
        try {
            val catalog = MaimaiSongCatalog.fromLxnsSongListJson(json)
            SongCatalogSnapshot(
                catalog = catalog,
                source = source,
                jsonBytes = json.toByteArray(Charsets.UTF_8).size,
            )
        } catch (error: Exception) {
            throw IOException(
                "Unable to parse ${source.logName} song catalog: ${
                    redactor.redact(error.message ?: error::class.java.simpleName)
                }",
                error,
            )
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
}

enum class SongCatalogSource(
    val logName: String,
) {
    FileCache("file-cache"),
    BundledFallback("bundled-fallback"),
    Network("network"),
}

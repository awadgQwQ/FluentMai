package dev.fluentmai.android

import dev.fluentmai.android.core.importer.MaimaiSongCatalog
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class LxnsMaimaiSongCatalogClient(
    private val redactor: PrivacyRedactor,
) {
    fun fetchCatalog(): MaimaiSongCatalog =
        MaimaiSongCatalog.fromLxnsSongListJson(fetchCatalogJson())

    fun fetchCatalogJson(): String {
        val response = requestWithRetries(SONG_LIST_URL, "song catalog")
        if (response.statusCode !in 200..299) {
            throw IOException("LXNS song catalog fetch failed: status=${response.statusCode}")
        }
        return response.body
    }

    fun fetchAliasJson(): String {
        val response = requestWithRetries(ALIAS_LIST_URL, "alias catalog")
        if (response.statusCode !in 200..299) {
            throw IOException("LXNS alias catalog fetch failed: status=${response.statusCode}")
        }
        return response.body
    }

    fun fetchYuzuAliasJson(): String {
        val response = requestWithRetries(YUZU_ALIAS_LIST_URL, "Yuzu alias catalog")
        if (response.statusCode !in 200..299) {
            throw IOException("Yuzu alias catalog fetch failed: status=${response.statusCode}")
        }
        return response.body
    }

    private fun requestWithRetries(rawUrl: String, resourceName: String): HttpResponse {
        var lastError: IOException? = null
        repeat(MAX_ATTEMPTS) { attempt ->
            try {
                return request(rawUrl, resourceName)
            } catch (error: IOException) {
                lastError = error
                if (attempt < MAX_ATTEMPTS - 1) {
                    Thread.sleep(RETRY_DELAY_MS * (attempt + 1))
                }
            }
        }
        throw lastError ?: IOException("LXNS $resourceName request failed")
    }

    private fun request(rawUrl: String, resourceName: String): HttpResponse =
        try {
            val connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "FluentMai Android")
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            HttpResponse(status, body)
        } catch (error: Exception) {
            throw IOException(
                "LXNS $resourceName request failed: ${redactor.redact(error.message ?: error::class.java.simpleName)}",
                error,
            )
        }

    private data class HttpResponse(
        val statusCode: Int,
        val body: String,
    )

    private companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_ATTEMPTS = 3
        private const val RETRY_DELAY_MS = 600L
        private const val SONG_LIST_URL = "https://maimai.lxns.net/api/v0/maimai/song/list?notes=true"
        private const val ALIAS_LIST_URL = "https://maimai.lxns.net/api/v0/maimai/alias/list"
        private const val YUZU_ALIAS_LIST_URL = "https://www.yuzuchan.moe/api/v2/aliases/maimaidx/aliases"
    }
}

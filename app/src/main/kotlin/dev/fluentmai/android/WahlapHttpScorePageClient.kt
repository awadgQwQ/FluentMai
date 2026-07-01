package dev.fluentmai.android

import android.util.Log
import dev.fluentmai.android.core.importer.WahlapScorePageUrls
import dev.fluentmai.android.core.model.Difficulty
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import java.io.IOException
import kotlinx.coroutines.runBlocking

class WahlapHttpScorePageClient(
    private val redactor: PrivacyRedactor,
    private val debugPageSink: (label: String, html: String) -> Unit = { _, _ -> },
) {
    fun login(authUrl: String) {
        val normalizedAuthUrl = normalizeWahlapAuthUrl(authUrl)
        Log.i(
            TAG,
            "Wahlap auth request: ${safeUrlSummary(normalizedAuthUrl)} " +
                "cookiesBefore=${cookieSummary()}",
        )
        val auth = request(label = "auth", rawUrl = normalizedAuthUrl)
        Log.i(TAG, "Wahlap auth response cookies=${cookieSummary()}")
        debugPageSink("auth", auth.body)
        if (auth.statusCode !in 200..299) {
            Log.w(
                TAG,
                "Wahlap auth returned status=${auth.statusCode} " +
                    "final=${safeUrlSummary(auth.finalUrl)}; checking home page before failing",
            )
        }

        Log.i(TAG, "Wahlap home request cookiesBefore=${cookieSummary()}")
        val home = request(label = "home", rawUrl = HOME_URL)
        Log.i(TAG, "Wahlap home response cookies=${cookieSummary()}")
        debugPageSink("home", home.body)
        if (home.statusCode !in 200..299) {
            throw IOException("Wahlap login failed: status=${home.statusCode}")
        }
        if (looksLikeAuthFailure(home.body)) {
            throw IOException("Wahlap login failed: home page is not authenticated")
        }
    }

    fun fetchScorePage(difficulty: Difficulty): String {
        val response = request(
            label = "score ${difficulty.name}",
            rawUrl = WahlapScorePageUrls.scorePageUrl(difficulty, incremental = true),
        )
        if (response.statusCode !in 200..299) {
            throw IOException("Wahlap score fetch failed: difficulty=${difficulty.name} status=${response.statusCode}")
        }
        if (response.contentType != null && !response.contentType.contains("html", ignoreCase = true)) {
            throw IOException("Wahlap score fetch failed: difficulty=${difficulty.name} unexpected content type")
        }
        if (looksLikeAuthFailure(response.body) || !looksLikeScorePage(response.body)) {
            throw IOException("Wahlap score fetch failed: difficulty=${difficulty.name} unexpected page")
        }
        return response.body
    }
    private fun request(label: String, rawUrl: String): HttpResponse =
        try {
            runBlocking {
                val response = WahlapKtorClient.getWahlapPage(rawUrl)
                HttpResponse(
                    statusCode = response.status.value,
                    contentType = response.headers[HttpHeaders.ContentType],
                    body = response.bodyAsText(),
                    finalUrl = response.call.request.url.toString(),
                )
            }
        } catch (error: Exception) {
            throw IOException("$label request failed: ${redactor.redact(error.message ?: error::class.java.simpleName)}", error)
        }

    private fun cookieSummary(): String =
        runBlocking { WahlapKtorClient.safeCookieSummary() }

    private fun looksLikeAuthFailure(html: String): Boolean {
        val normalized = html.lowercase()
        return normalized.contains("please open in wechat") ||
            normalized.contains("/wc_auth/oauth/authorize/") ||
            normalized.contains("open.weixin.qq.com/connect/oauth2/authorize") ||
            normalized.contains("title_error")
    }

    private fun looksLikeScorePage(html: String): Boolean =
        html.contains("musicDetail", ignoreCase = true) &&
            html.contains("music_name_block", ignoreCase = true) &&
            html.contains("music_score_block", ignoreCase = true)
    private fun safeUrlSummary(url: String): String =
        runCatching {
            val uri = java.net.URI(url)
            val query = uri.rawQuery.orEmpty().lowercase()
            val path = uri.rawPath.orEmpty()
            "scheme=${uri.scheme} host=${uri.host} path=$path " +
                "hasCode=${query.contains("code=")} hasState=${query.contains("state=")} " +
                "duplicatedHttpInPath=${path.lowercase().contains("http://")}"
        }.getOrElse { "unparseable" }

    private data class HttpResponse(
        val statusCode: Int,
        val contentType: String?,
        val body: String,
        val finalUrl: String,
    )

    private companion object {
        private const val TAG = "WahlapHttpScore"
        private const val HOME_URL = "https://maimai.wahlap.com/maimai-mobile/home/"
    }
}

internal fun normalizeWahlapAuthUrl(authUrl: String): String {
    val trimmed = authUrl.trim()
    if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
        throw IllegalArgumentException("Captured auth URL must start with http:// or https://")
    }
    if (trimmed.startsWith("http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx", ignoreCase = true)) {
        return "https://" + trimmed.removePrefix("http://")
    }
    return trimmed
}

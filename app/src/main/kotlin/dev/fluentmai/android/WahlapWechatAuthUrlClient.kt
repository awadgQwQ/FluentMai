package dev.fluentmai.android

import android.util.Log
import dev.fluentmai.android.core.privacy.PrivacyRedactor
import java.io.IOException
import kotlinx.coroutines.runBlocking

class WahlapWechatAuthUrlClient(
    private val redactor: PrivacyRedactor,
) {
    fun maimaiDxAuthUrl(): String {
        try {
            return runBlocking {
                val finalUrl = WahlapKtorClient.getAuthUrl()
                Log.i(
                    TAG,
                    "Generated Wahlap auth URL final=${safeUrlSummary(finalUrl)} " +
                        "cookies=${WahlapKtorClient.safeCookieSummary()}",
                )

                if (!finalUrl.contains("tgk-wcaime.wahlap.com", ignoreCase = true) &&
                    !finalUrl.contains("maimai-dx", ignoreCase = true)
                ) {
                    throw IOException("Unexpected Wahlap auth redirect")
                }

                finalUrl.replace("redirect_uri=https", "redirect_uri=http")
            }
        } catch (error: Exception) {
            throw IOException(
                "生成舞萌微信授权地址失败：${redactor.redact(error.message ?: error::class.java.simpleName)}",
                error,
            )
        }
    }

    private companion object {
        private const val TAG = "WahlapAuthUrl"

        private fun safeUrlSummary(url: String): String =
            runCatching {
                val uri = java.net.URI(url)
                val query = uri.rawQuery.orEmpty().lowercase()
                "scheme=${uri.scheme} host=${uri.host} path=${uri.rawPath.orEmpty()} " +
                    "hasRedirectUri=${query.contains("redirect_uri=")} " +
                    "hasCode=${query.contains("code=")} hasState=${query.contains("state=")}"
            }.getOrElse { "unparseable" }
    }
}

package dev.fluentmai.android

import io.ktor.http.HttpHeaders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class WahlapCookieImportCredentialsTest {
    @Test
    fun parseAcceptsRawCookieString() {
        val credentials = WahlapCookieImportCredentials.parse("_t=token-value; userId=12345; other=ok")

        assertEquals("token-value", credentials.cookies["_t"])
        assertEquals("12345", credentials.cookies["userId"])
        assertEquals("_t=token-value; userId=12345; other=ok", credentials.cookieHeader)
    }

    @Test
    fun parseAcceptsReqableRequestHeaders() {
        val credentials = WahlapCookieImportCredentials.parse(
            """
            GET /maimai-mobile/home/ HTTP/2
            :authority: maimai.wahlap.com
            user-agent: WeChat Test UA
            accept-language: zh-CN,zh;q=0.9
            referer: https://maimai.wahlap.com/maimai-mobile/home/
            cookie: foo=bar; _t=request-token; userId=67890
            """.trimIndent(),
        )

        assertEquals("request-token", credentials.cookies["_t"])
        assertEquals("67890", credentials.cookies["userId"])
        assertEquals("WeChat Test UA", credentials.headers[HttpHeaders.UserAgent])
        assertEquals("zh-CN,zh;q=0.9", credentials.headers[HttpHeaders.AcceptLanguage])
        assertEquals("https://maimai.wahlap.com/maimai-mobile/home/", credentials.headers[HttpHeaders.Referrer])
    }

    @Test
    fun parseAcceptsSetCookieHeadersWithoutCookieAttributes() {
        val credentials = WahlapCookieImportCredentials.parse(
            """
            set-cookie: _t=response-token; Path=/; HttpOnly
            set-cookie: userId=24680; Path=/; Expires=Tue, 30 Jun 2026 00:00:00 GMT
            """.trimIndent(),
        )

        assertEquals("_t=response-token; userId=24680", credentials.cookieHeader)
        assertFalse(credentials.cookies.containsKey("Path"))
        assertFalse(credentials.cookies.containsKey("Expires"))
    }

    @Test
    fun parseRejectsCookieMissingRequiredFields() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            WahlapCookieImportCredentials.parse("_t=token-value; other=ok")
        }

        assertEquals("Wahlap Cookie missing required fields: userId", error.message)
    }
}

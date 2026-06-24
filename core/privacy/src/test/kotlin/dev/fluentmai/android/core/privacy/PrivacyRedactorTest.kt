package dev.fluentmai.android.core.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRedactorTest {
    private val redactor = PrivacyRedactor()

    @Test
    fun removesCookiesTokensHtmlAuthUrlsAndInputValues() {
        val redacted = redactor.redact(
            """
            Cookie: mai_auth_cookie=secret-cookie
            Token=super-token
            https://maimai.wahlap.com/maimai-mobile/?ticket=full-auth-ticket&token=query-token
            <html><body><form><input value="typed-secret"></form></body></html>
            input value='typed-again'
            """.trimIndent(),
        )

        assertFalse(redacted.contains("Cookie", ignoreCase = true))
        assertFalse(redacted.contains("Token", ignoreCase = true))
        assertFalse(redacted.contains("secret-cookie"))
        assertFalse(redacted.contains("super-token"))
        assertFalse(redacted.contains("full-auth-ticket"))
        assertFalse(redacted.contains("typed-secret"))
        assertFalse(redacted.contains("typed-again"))
        assertFalse(redacted.contains("<html", ignoreCase = true))
        assertFalse(redacted.contains("<input", ignoreCase = true))
        assertTrue(redacted.contains("[REDACTED_SECRET]"))
        assertTrue(redacted.contains("[REDACTED_AUTH_URL]"))
        assertTrue(redacted.contains("[REDACTED_INPUT_VALUE]"))
    }

    @Test
    fun removesLocalHookUrlsAndHtmlBodyText() {
        val redacted = redactor.redact(
            """
            authUrl=http://127.0.0.1:8284/auth/maimai?random=123&token=local-secret
            Set-Cookie: maimai_session=private-cookie
            <html><body>private score page content</body></html>
            """.trimIndent(),
        )

        assertFalse(redacted.contains("127.0.0.1"))
        assertFalse(redacted.contains("random=123"))
        assertFalse(redacted.contains("local-secret"))
        assertFalse(redacted.contains("private-cookie"))
        assertFalse(redacted.contains("private score page content"))
        assertTrue(redacted.contains("[REDACTED_AUTH_URL]"))
        assertTrue(redacted.contains("[REDACTED_SECRET]"))
        assertTrue(redacted.contains("[REDACTED_HTML]"))
    }
}

package dev.fluentmai.android.core.privacy

class PrivacyRedactor {
    fun redact(message: String): String =
        message
            .redactCredentialFields()
            .redactAuthUrls()
            .redactInputValues()
            .redactHtml()

    private fun String.redactCredentialFields(): String =
        replace(credentialFieldRegex, "[REDACTED_SECRET]")

    private fun String.redactAuthUrls(): String =
        replace(authUrlRegex, "[REDACTED_AUTH_URL]")

    private fun String.redactInputValues(): String =
        replace(inputValueRegex, "[REDACTED_INPUT_VALUE]")

    private fun String.redactHtml(): String =
        replace(htmlTagRegex, "[REDACTED_HTML]")

    companion object {
        private val credentialFieldRegex =
            Regex(
                pattern = """(?i)\b(cookie|set-cookie|token|authorization)\b\s*[:=]\s*("[^"]*"|'[^']*'|[^\s;,]+)""",
            )

        private val authUrlRegex =
            Regex(
                pattern = """(?i)https?://[^\s"'<>]*(wahlap|maimai|auth|login|ticket|token)[^\s"'<>]*""",
            )

        private val inputValueRegex =
            Regex(
                pattern = """(?i)input\s+value\s*=\s*("[^"]*"|'[^']*'|[^\s;,>]+)""",
            )

        private val htmlTagRegex =
            Regex(
                pattern = """(?is)</?\s*(html|body|form|input|script|head|meta|div|span|table|tr|td|a)\b[^>]*>""",
            )
    }
}

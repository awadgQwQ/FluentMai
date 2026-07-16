from __future__ import annotations

import logging
import re


MAX_SAFE_MESSAGE = 500


class PrivacyRedactor:
    credential_field_re = re.compile(
        r"(?i)\b(cookie|set-cookie|token|authorization|import-token|x-user-token)\b\s*[:=]\s*"
        r"(\"[^\"]*\"|'[^']*'|[^\s;,}]+)"
    )
    auth_url_re = re.compile(
        r"(?i)https?://[^\s\"'<>]*(wahlap|maimai|auth|login|ticket|token|code=|state=)[^\s\"'<>]*"
    )
    html_block_re = re.compile(r"(?is)<\s*(html|body|form|script|head)\b[^>]*>.*?</\s*\1\s*>")
    html_tag_re = re.compile(r"(?is)</?\s*(html|body|form|input|script|head|meta|div|span|table|tr|td|a)\b[^>]*>")

    def redact(self, message: object) -> str:
        text = "" if message is None else str(message)
        text = self.credential_field_re.sub(lambda m: f"{m.group(1)}=[REDACTED_SECRET]", text)
        text = self.auth_url_re.sub("[REDACTED_AUTH_URL]", text)
        text = self.html_block_re.sub("[REDACTED_HTML]", text)
        text = self.html_tag_re.sub("[REDACTED_HTML]", text)
        text = re.sub(r"\s+", " ", text).strip()
        if len(text) > MAX_SAFE_MESSAGE:
            text = text[:MAX_SAFE_MESSAGE] + "..."
        return text


redactor = PrivacyRedactor()


class RedactingLogFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        if record.args:
            record.args = tuple(redactor.redact(arg) for arg in record.args)
        record.msg = redactor.redact(record.msg)
        return True


def install_redacting_filter(logger: logging.Logger | None = None) -> None:
    target = logger or logging.getLogger()
    if not any(isinstance(item, RedactingLogFilter) for item in target.filters):
        target.addFilter(RedactingLogFilter())

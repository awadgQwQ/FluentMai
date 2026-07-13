package dev.fluentmai.android.core.model

import java.text.Normalizer

internal actual fun normalizeUnicodeCompatibility(value: String): String =
    Normalizer.normalize(value, Normalizer.Form.NFKC)

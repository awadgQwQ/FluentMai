package dev.fluentmai.android.core.model

import platform.Foundation.NSString

internal actual fun normalizeUnicodeCompatibility(value: String): String =
    (value as NSString).precomposedStringWithCompatibilityMapping

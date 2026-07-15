package dev.fluentmai.android.core.model

import kotlin.test.Test
import kotlin.test.assertEquals

class UnicodeNormalizationTest {
    @Test
    fun compatibilityCharactersNormalizeWithNfkc() {
        assertEquals("ABC 123", normalizeUnicodeCompatibility("ＡＢＣ　１２３"))
        assertEquals("ffi 1", normalizeUnicodeCompatibility("ﬃ ①"))
    }

    @Test
    fun combiningCharactersNormalizeToPrecomposedForm() {
        assertEquals("é", normalizeUnicodeCompatibility("e\u0301"))
    }
}

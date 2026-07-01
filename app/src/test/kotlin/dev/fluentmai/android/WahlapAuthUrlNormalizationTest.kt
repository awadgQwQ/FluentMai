package dev.fluentmai.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class WahlapAuthUrlNormalizationTest {
    @Test
    fun capturedHttpCallbackIsReplayedOverHttps() {
        val authUrl =
            "http://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?r=abc&t=260701" +
                "&code=wx-code&state=state-value"
        val expected =
            "https://tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx?r=abc&t=260701" +
                "&code=wx-code&state=state-value"

        assertEquals(expected, normalizeWahlapAuthUrl("  $authUrl  "))
    }

    @Test
    fun rejectsNonHttpUrl() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeWahlapAuthUrl("tgk-wcaime.wahlap.com/wc_auth/oauth/callback/maimai-dx")
        }
    }
}

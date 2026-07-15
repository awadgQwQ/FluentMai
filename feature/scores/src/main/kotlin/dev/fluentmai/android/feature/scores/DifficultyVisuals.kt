package dev.fluentmai.android.feature.scores

import androidx.compose.ui.graphics.Color
import dev.fluentmai.android.core.model.Difficulty

/** Shared difficulty tokens for B50 cards, chart cards, plate rows and details. */
internal fun Difficulty.accentColor(): Color = when (this) {
    Difficulty.BASIC -> Color(0xFF2F9E44)
    Difficulty.ADVANCED -> Color(0xFFD9480F)
    Difficulty.EXPERT -> Color(0xFFE03131)
    Difficulty.MASTER -> Color(0xFF7B2CBF)
    Difficulty.RE_MASTER -> Color(0xFF9D4EDD)
}

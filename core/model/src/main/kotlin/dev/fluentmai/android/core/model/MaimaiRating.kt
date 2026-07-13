package dev.fluentmai.android.core.model

import kotlin.math.floor
import kotlin.math.min

@Suppress("UNUSED_PARAMETER")
fun calculateDxRating(
    levelValue: Double,
    achievement: Double,
    comboFlag: String? = null,
): Int {
    val cappedAchievement = min(achievement, MAX_RATING_ACHIEVEMENT)
    return floor(levelValue * (cappedAchievement / 100.0) * dxRatingCoefficient(cappedAchievement)).toInt()
}

fun dxRatingCoefficient(achievement: Double): Double =
    when {
        achievement >= 100.5 -> 22.4
        achievement >= 100.4999 -> 22.2
        achievement >= 100.0 -> 21.6
        achievement >= 99.9999 -> 21.4
        achievement >= 99.5 -> 21.1
        achievement >= 99.0 -> 20.8
        achievement >= 98.9999 -> 20.6
        achievement >= 98.0 -> 20.3
        achievement >= 97.0 -> 20.0
        achievement >= 96.9999 -> 17.6
        achievement >= 94.0 -> 16.8
        achievement >= 90.0 -> 15.2
        achievement >= 80.0 -> 13.6
        achievement >= 79.9999 -> 12.8
        achievement >= 75.0 -> 12.0
        achievement >= 70.0 -> 11.2
        achievement >= 60.0 -> 9.6
        achievement >= 50.0 -> 8.0
        achievement >= 40.0 -> 6.4
        achievement >= 30.0 -> 4.8
        achievement >= 20.0 -> 3.2
        achievement >= 10.0 -> 1.6
        else -> 0.0
    }

private const val MAX_RATING_ACHIEVEMENT = 100.5

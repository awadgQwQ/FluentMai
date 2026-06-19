package dev.fluentmai.android.core.model

enum class Difficulty(val levelIndex: Int) {
    BASIC(0),
    ADVANCED(1),
    EXPERT(2),
    MASTER(3),
    RE_MASTER(4);

    companion object {
        fun fromLevelIndex(levelIndex: Int): Difficulty? =
            entries.firstOrNull { it.levelIndex == levelIndex }

        fun fromWireName(value: String): Difficulty? =
            when (value.trim().uppercase().replace(":", "_").replace(" ", "_")) {
                "BASIC" -> BASIC
                "ADVANCED" -> ADVANCED
                "EXPERT" -> EXPERT
                "MASTER" -> MASTER
                "RE_MASTER", "REMASTER" -> RE_MASTER
                else -> null
            }
    }
}

